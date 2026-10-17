package br.edu.udf.cs.sinal.chamado.infrastructure.query;

import br.edu.udf.cs.sinal.chamado.application.query.ChamadoConsulta;
import br.edu.udf.cs.sinal.chamado.application.query.ChamadoHistoricoItem;
import br.edu.udf.cs.sinal.chamado.application.query.PosicaoNoHistorico;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// A ordem (data_criacao DESC, id ASC) é exatamente a de idx_chamado_historico (usuario_id, data_criacao DESC, id):
// o MySQL percorre o índice sem ordenação extra. Instantes em UTC, como gravados pelo Hibernate (DATETIME(6)).
@Repository
public class ChamadoConsultaJdbc implements ChamadoConsulta {

    private static final String SELECAO = """
            SELECT c.id, c.modalidade, c.orgao, c.ocorrencia_id, o.nome AS ocorrencia_nome, c.estado,
                   c.baixa_precisao, c.data_criacao
            FROM chamado c LEFT JOIN ocorrencia_tipo o ON o.id = c.ocorrencia_id
            WHERE c.usuario_id = ?
            """;
    private static final String CONTINUACAO = "  AND (c.data_criacao < ? OR (c.data_criacao = ? AND c.id > ?))\n";
    private static final String ORDENACAO = "ORDER BY c.data_criacao DESC, c.id ASC LIMIT ?";

    static final String SQL_INICIO = SELECAO + ORDENACAO;
    static final String SQL_CONTINUACAO = SELECAO + CONTINUACAO + ORDENACAO;

    private static final RowMapper<ChamadoHistoricoItem> MAPEADOR = (rs, linha) -> new ChamadoHistoricoItem(
            rs.getLong("id"),
            ModalidadeChamado.valueOf(rs.getString("modalidade")),
            OrgaoSeguranca.valueOf(rs.getString("orgao")),
            rs.getObject("ocorrencia_id", Long.class),
            rs.getString("ocorrencia_nome"),
            EstadoChamado.valueOf(rs.getString("estado")),
            rs.getBoolean("baixa_precisao"),
            rs.getObject("data_criacao", LocalDateTime.class).toInstant(ZoneOffset.UTC));

    private final JdbcTemplate jdbc;

    public ChamadoConsultaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChamadoHistoricoItem> listarHistorico(Long usuarioId, PosicaoNoHistorico depoisDe, int quantidade) {
        if (depoisDe == null) {
            return jdbc.query(SQL_INICIO, MAPEADOR, usuarioId, quantidade);
        }
        LocalDateTime dataCriacao = emUtc(depoisDe.dataCriacao());
        return jdbc.query(SQL_CONTINUACAO, MAPEADOR, usuarioId, dataCriacao, dataCriacao, depoisDe.id(), quantidade);
    }

    private static LocalDateTime emUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
