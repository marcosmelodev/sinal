package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.EncaminhamentoGateway;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.ResultadoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.SolicitacaoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.repository.EncaminhamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

// Processamento técnico do encaminhamento SIMULADO (Etapa 4 §18): nenhum órgão real é acionado.
// Ordem de bloqueio fixa em todos os caminhos: Chamado antes do Encaminhamento (Etapa 7.5).
// Cada operação usa a própria transação, por Chamado, e é idempotente: repetir não aplica nada duas vezes.
@Service
public class EncaminhamentoService {

    private static final Logger LOG = LoggerFactory.getLogger(EncaminhamentoService.class);
    private static final String RESTRICAO_EN1 = "uk_encaminhamento_chamado";

    private final ChamadoRepository chamadoRepository;
    private final EncaminhamentoRepository encaminhamentoRepository;
    private final EncaminhamentoGateway encaminhamentoGateway;
    private final DeterminacaoDeCentralService determinacaoDeCentralService;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public EncaminhamentoService(ChamadoRepository chamadoRepository, EncaminhamentoRepository encaminhamentoRepository,
                                 EncaminhamentoGateway encaminhamentoGateway,
                                 DeterminacaoDeCentralService determinacaoDeCentralService, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.chamadoRepository = chamadoRepository;
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.encaminhamentoGateway = encaminhamentoGateway;
        this.determinacaoDeCentralService = determinacaoDeCentralService;
        this.clock = clock;
        // REQUIRES_NEW: transação própria por Chamado, inclusive quando chamado de um ouvinte AFTER_COMMIT,
        // em que uma transação REQUIRED se juntaria à transação já confirmada e não gravaria nada.
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Cria o Encaminhamento AGENDADO de um Chamado CRIADO. Chamado pelo ouvinte de ChamadoRegistrado e pela
    // reconciliação. Devolve true somente se o Encaminhamento foi criado nesta chamada.
    public boolean iniciar(Long chamadoId) {
        Optional<Chamado> lido = chamadoRepository.buscarPorId(chamadoId);
        if (lido.isEmpty() || lido.get().getEstado() != EstadoChamado.CRIADO
                || encaminhamentoRepository.buscarPorChamadoId(chamadoId).isPresent()) {
            return false;
        }
        // A consulta de jurisdição fica fora do bloqueio: uma fonte real lenta não seguraria a linha do Chamado.
        CentralDeterminada central = determinacaoDeCentralService.determinar(lido.get().getLocalizacao(),
                lido.get().getOrgao());
        try {
            return Boolean.TRUE.equals(transacao.execute(status -> criar(chamadoId, central)));
        } catch (DataIntegrityViolationException e) {
            // EN1: outro fluxo criou o Encaminhamento primeiro; o UNIQUE(chamado_id) decidiu.
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT).contains(RESTRICAO_EN1)) {
                throw e;
            }
            return false;
        }
    }

    private boolean criar(Long chamadoId, CentralDeterminada central) {
        Optional<Chamado> bloqueado = chamadoRepository.buscarPorIdParaAlteracao(chamadoId);
        // Reconfere sob o bloqueio: outro fluxo pode ter criado o Encaminhamento enquanto a central era determinada.
        if (bloqueado.isEmpty() || bloqueado.get().getEstado() != EstadoChamado.CRIADO
                || encaminhamentoRepository.buscarPorChamadoId(chamadoId).isPresent()) {
            return false;
        }
        Chamado chamado = bloqueado.get();
        Instant agora = Instant.now(clock);
        ResultadoEncaminhamento resultado = encaminhamentoGateway.encaminhar(new SolicitacaoEncaminhamento(chamadoId,
                chamado.getOrgao(), chamado.getOcorrenciaId(), chamado.getLocalizacao(), central.centralCodigo()), agora);
        encaminhamentoRepository.salvar(Encaminhamento.agendar(chamadoId, central.jurisdicaoCodigo(),
                central.centralCodigo(), agora, resultado.proximaTransicaoEm()));
        LOG.info("Encaminhamento simulado agendado para o chamado {}", chamadoId);
        return true;
    }

    // Aplica a próxima etapa vencida (Etapa 5 §8). Devolve true somente se algo foi aplicado nesta chamada.
    public boolean avancar(Long chamadoId) {
        return Boolean.TRUE.equals(transacao.execute(status -> avancarSobBloqueio(chamadoId)));
    }

    private boolean avancarSobBloqueio(Long chamadoId) {
        Optional<Chamado> bloqueado = chamadoRepository.buscarPorIdParaAlteracao(chamadoId);
        if (bloqueado.isEmpty()) {
            return false;
        }
        Optional<Encaminhamento> agendado = encaminhamentoRepository.buscarPorChamadoIdParaAlteracao(chamadoId);
        // O instante é lido depois dos bloqueios; outra instância pode ter avançado enquanto esperávamos.
        Instant agora = Instant.now(clock);
        if (agendado.isEmpty() || agendado.get().getStatus() != StatusEncaminhamento.AGENDADO
                || agendado.get().getProximaTransicaoEm().isAfter(agora)) {
            return false;
        }
        Chamado chamado = bloqueado.get();
        Encaminhamento encaminhamento = agendado.get();
        EstadoChamado anterior = chamado.getEstado();
        switch (anterior) {
            case CRIADO -> {
                chamado.encaminharSimulado(agora);
                encaminhamento.reagendar(encaminhamentoGateway.agendarProximaEtapa(chamadoId, agora));
                chamadoRepository.salvar(chamado);
            }
            case ENCAMINHADO_SIMULADO -> {
                chamado.encerrar(agora);
                encaminhamento.concluir();
                chamadoRepository.salvar(chamado);
            }
            // Reparo: Chamado já encerrado com Encaminhamento ainda agendado; apenas conclui o processamento.
            case ENCERRADO -> encaminhamento.concluir();
        }
        encaminhamentoRepository.salvar(encaminhamento);
        LOG.info("Simulação do chamado {}: {} -> {}", chamadoId, anterior, chamado.getEstado());
        return true;
    }
}
