package br.edu.udf.cs.sinal.chamado.application;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// Registro de Chamado (PADRAO e Emergência Imediata) com idempotência (Etapa 4 §17):
// mesma chave e mesmos dados = replay; mesma chave e dados diferentes = IDEMPOTENCIA_DIVERGENTE.
// Transações programáticas: após a violação do UNIQUE a transação de escrita é desfeita, e o replay
// precisa reler o Chamado vencedor em uma nova transação.
@Service
public class RegistrarChamadoService {

    public static final String IDEMPOTENCIA_DIVERGENTE = "IDEMPOTENCIA_DIVERGENTE";
    public static final String OCORRENCIA_INVALIDA = "OCORRENCIA_INVALIDA";
    public static final String CABECALHO_IDEMPOTENCIA = "Idempotency-Key";

    private static final Pattern FORMATO_CHAVE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final String RESTRICAO_IDEMPOTENCIA = "uk_chamado_usuario_chave_idempotencia";

    private final ChamadoRepository chamadoRepository;
    private final CatalogoDeOcorrencias catalogoDeOcorrencias;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Float limiarBaixaPrecisaoMetros;
    private final TransactionTemplate escrita;
    private final TransactionTemplate leitura;

    public RegistrarChamadoService(ChamadoRepository chamadoRepository, CatalogoDeOcorrencias catalogoDeOcorrencias,
                                   ApplicationEventPublisher eventos, Clock clock,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${sinal.localizacao.limiar-baixa-precisao-metros}")
                                   Float limiarBaixaPrecisaoMetros) {
        this.chamadoRepository = chamadoRepository;
        this.catalogoDeOcorrencias = catalogoDeOcorrencias;
        this.eventos = eventos;
        this.clock = clock;
        this.limiarBaixaPrecisaoMetros = limiarBaixaPrecisaoMetros;
        this.escrita = new TransactionTemplate(transactionManager);
        this.leitura = new TransactionTemplate(transactionManager);
        this.leitura.setReadOnly(true);
    }

    public ChamadoRegistradoResultado registrarPadrao(Long usuarioId, String chaveIdempotencia, OrgaoSeguranca orgao,
                                                      Long ocorrenciaId, String detalheAdicional,
                                                      LocalizacaoInformada localizacao) {
        validarChave(chaveIdempotencia);
        Chamado candidato = Chamado.registrar(usuarioId, orgao, ocorrenciaId, paraDominio(localizacao),
                detalheAdicional, limiarBaixaPrecisaoMetros, Instant.now(clock));
        return registrar(usuarioId, chaveIdempotencia, candidato, true);
    }

    // CH2: sempre PM, sem ocorrência e sem detalhe; não consulta o catálogo.
    public ChamadoRegistradoResultado registrarEmergenciaImediata(Long usuarioId, String chaveIdempotencia,
                                                                  LocalizacaoInformada localizacao) {
        validarChave(chaveIdempotencia);
        Chamado candidato = Chamado.registrarEmergenciaImediata(usuarioId, paraDominio(localizacao),
                limiarBaixaPrecisaoMetros, Instant.now(clock));
        return registrar(usuarioId, chaveIdempotencia, candidato, false);
    }

    private ChamadoRegistradoResultado registrar(Long usuarioId, String chave, Chamado candidato,
                                                 boolean validarOcorrencia) {
        // A idempotência vem antes do catálogo: um reenvio legítimo é replay mesmo que a ocorrência mude depois.
        Optional<Chamado> existente = buscarPorChave(usuarioId, chave);
        if (existente.isPresent()) {
            return replay(existente.get(), candidato);
        }
        if (validarOcorrencia
                && !catalogoDeOcorrencias.ocorrenciaAtivaDoOrgao(candidato.getOcorrenciaId(), candidato.getOrgao())) {
            throw new AplicacaoException(OCORRENCIA_INVALIDA, "A ocorrência informada não é válida para o órgão.",
                    Set.of("ocorrenciaId"));
        }
        try {
            return escrita.execute(status -> {
                Chamado salvo = chamadoRepository.salvarNovo(candidato, chave);
                eventos.publishEvent(new ChamadoRegistrado(salvo.getId(), salvo.getDataCriacao()));
                return ChamadoRegistradoResultado.de(salvo, false);
            });
        } catch (DataIntegrityViolationException e) {
            // Corrida: outra requisição com a mesma chave gravou primeiro; o UNIQUE decidiu.
            if (!violouChaveDeIdempotencia(e)) {
                throw e;
            }
            Chamado vencedor = buscarPorChave(usuarioId, chave).orElseThrow(() -> e);
            return replay(vencedor, candidato);
        }
    }

    private Optional<Chamado> buscarPorChave(Long usuarioId, String chave) {
        return leitura.execute(status -> chamadoRepository.buscarPorUsuarioEChaveIdempotencia(usuarioId, chave));
    }

    // Compara somente os dados enviados pelo cliente; dataCriacao e baixaPrecisao são calculados no servidor.
    private static ChamadoRegistradoResultado replay(Chamado existente, Chamado candidato) {
        boolean mesmoPayload = existente.getModalidade() == candidato.getModalidade()
                && existente.getOrgao() == candidato.getOrgao()
                && Objects.equals(existente.getOcorrenciaId(), candidato.getOcorrenciaId())
                && Objects.equals(existente.getDetalheAdicional(), candidato.getDetalheAdicional())
                && existente.getLocalizacao().equals(candidato.getLocalizacao());
        if (!mesmoPayload) {
            throw new AplicacaoException(IDEMPOTENCIA_DIVERGENTE,
                    "A chave de idempotência já foi usada com outros dados.", Set.of());
        }
        return ChamadoRegistradoResultado.de(existente, true);
    }

    private static boolean violouChaveDeIdempotencia(DataIntegrityViolationException e) {
        return String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT)
                .contains(RESTRICAO_IDEMPOTENCIA);
    }

    private static void validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new AplicacaoException("CAMPO_OBRIGATORIO", "Campo obrigatório.", Set.of(CABECALHO_IDEMPOTENCIA));
        }
        if (!FORMATO_CHAVE.matcher(chave).matches()) {
            throw new AplicacaoException("VALOR_INVALIDO", "Valor inválido.", Set.of(CABECALHO_IDEMPOTENCIA));
        }
    }

    // O instante da captura é truncado em microssegundos (DATETIME(6)): o valor gravado é exatamente
    // o comparado em um replay, sem o arredondamento do MySQL.
    private static Localizacao paraDominio(LocalizacaoInformada informada) {
        if (informada == null) {
            return null;
        }
        Instant timestamp = informada.timestamp() == null ? null : informada.timestamp().truncatedTo(ChronoUnit.MICROS);
        return new Localizacao(informada.latitude(), informada.longitude(), informada.precisao(), timestamp);
    }
}
