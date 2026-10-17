package br.edu.udf.cs.sinal.chamado.application.query;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

// Histórico do usuário (Etapa 4 §16), paginado por cursor: estável enquanto novos chamados são registrados
// ou o status muda durante o polling. O cursor é opaco para o cliente.
@Service
public class ListarChamadosHistoricoQuery {

    public static final int LIMITE_PADRAO = 20;
    public static final int LIMITE_MAXIMO = 50;

    private final ChamadoConsulta chamadoConsulta;

    public ListarChamadosHistoricoQuery(ChamadoConsulta chamadoConsulta) {
        this.chamadoConsulta = chamadoConsulta;
    }

    @Transactional(readOnly = true)
    public PaginaDeHistorico executar(Long usuarioId, Integer limite, String cursor) {
        int tamanho = validarLimite(limite);
        PosicaoNoHistorico depoisDe = cursor == null ? null : decodificar(cursor);
        // Um item a mais indica se há próxima página, sem contagem separada.
        List<ChamadoHistoricoItem> linhas = chamadoConsulta.listarHistorico(usuarioId, depoisDe, tamanho + 1);
        if (linhas.size() <= tamanho) {
            return new PaginaDeHistorico(linhas, null);
        }
        List<ChamadoHistoricoItem> itens = linhas.subList(0, tamanho);
        ChamadoHistoricoItem ultimo = itens.get(itens.size() - 1);
        return new PaginaDeHistorico(itens, codificar(new PosicaoNoHistorico(ultimo.dataCriacao(), ultimo.id())));
    }

    private static int validarLimite(Integer limite) {
        if (limite == null) {
            return LIMITE_PADRAO;
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw valorInvalido("limite");
        }
        return limite;
    }

    // Formato interno: base64url("<dataCriacao em microssegundos desde a época>.<id>"), sem padding.
    static String codificar(PosicaoNoHistorico posicao) {
        Instant data = posicao.dataCriacao();
        long micros = Math.addExact(Math.multiplyExact(data.getEpochSecond(), 1_000_000L), data.getNano() / 1_000);
        String conteudo = micros + "." + posicao.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(conteudo.getBytes(StandardCharsets.US_ASCII));
    }

    static PosicaoNoHistorico decodificar(String cursor) {
        try {
            String conteudo = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!conteudo.matches("-?\\d{1,19}\\.[1-9]\\d{0,18}")) {
                throw valorInvalido("cursor");
            }
            String[] partes = conteudo.split("\\.");
            long micros = Long.parseLong(partes[0]);
            Instant data = Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                    Math.floorMod(micros, 1_000_000L) * 1_000L);
            return new PosicaoNoHistorico(data, Long.parseLong(partes[1]));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw valorInvalido("cursor");
        }
    }

    private static AplicacaoException valorInvalido(String campo) {
        return new AplicacaoException("VALOR_INVALIDO", "Valor inválido.", Set.of(campo));
    }
}
