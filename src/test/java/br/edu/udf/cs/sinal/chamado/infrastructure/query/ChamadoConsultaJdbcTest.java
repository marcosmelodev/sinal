package br.edu.udf.cs.sinal.chamado.infrastructure.query;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.chamado.application.query.ChamadoHistoricoItem;
import br.edu.udf.cs.sinal.chamado.application.query.PosicaoNoHistorico;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.chamado.infrastructure.persistence.ChamadoPersistenceAdapter;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Os chamados são gravados pelo adapter real (Hibernate, UTC): confirma que a leitura JDBC enxerga os mesmos instantes.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ChamadoPersistenceAdapter.class, ChamadoConsultaJdbc.class})
class ChamadoConsultaJdbcTest {

    private static final Instant T0 = Instant.parse("2026-10-17T12:00:00.123456Z");

    @Autowired
    private ChamadoConsultaJdbc consulta;

    @Autowired
    private ChamadoPersistenceAdapter chamados;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private Long usuarioA;
    private Long usuarioB;
    private Long ocorrenciaSamu;
    private int chaves;

    @BeforeEach
    void prepararDados() {
        usuarioA = inserirUsuario("52998224725", "a@exemplo.com");
        usuarioB = inserirUsuario("11144477735", "b@exemplo.com");
        ocorrenciaSamu = jdbc.queryForObject("SELECT id FROM ocorrencia_tipo WHERE codigo = 'SAMU_CONVULSAO'", Long.class);
    }

    private Long inserirUsuario(String cpf, String email) {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", cpf, email, "hash", true, Timestamp.from(T0));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, cpf);
    }

    private static Localizacao localizacao(float precisao) {
        return new Localizacao(-15.79, -47.88, precisao, T0);
    }

    private Long padrao(Long usuarioId, Instant criadoEm, float precisao) {
        return chamados.salvarNovo(Chamado.registrar(usuarioId, OrgaoSeguranca.SAMU, ocorrenciaSamu,
                localizacao(precisao), null, 100.0f, criadoEm), "c" + (++chaves)).getId();
    }

    private Long emergencia(Long usuarioId, Instant criadoEm) {
        return chamados.salvarNovo(Chamado.registrarEmergenciaImediata(usuarioId, localizacao(25.0f), 100.0f, criadoEm),
                "c" + (++chaves)).getId();
    }

    private List<Long> ids(List<ChamadoHistoricoItem> itens) {
        return itens.stream().map(ChamadoHistoricoItem::id).toList();
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void deveOrdenarDoMaisRecenteAoMaisAntigo() {
        Long antigo = padrao(usuarioA, T0, 25.0f);
        Long recente = padrao(usuarioA, T0.plusSeconds(120), 25.0f);
        Long meio = emergencia(usuarioA, T0.plusSeconds(60));
        sincronizar();

        assertThat(ids(consulta.listarHistorico(usuarioA, null, 10))).containsExactly(recente, meio, antigo);
    }

    @Test
    void empateNaDataDeveSerDesempatadoPorIdCrescente() {
        Long primeiro = padrao(usuarioA, T0, 25.0f);
        Long segundo = padrao(usuarioA, T0, 25.0f);
        Long anterior = padrao(usuarioA, T0.minusSeconds(1), 25.0f);
        sincronizar();

        assertThat(ids(consulta.listarHistorico(usuarioA, null, 10))).containsExactly(primeiro, segundo, anterior);
    }

    @Test
    void continuacaoDeveComecarLogoAposAPosicaoSemRepetirNemPular() {
        List<Long> esperados = new ArrayList<>();
        for (int i = 4; i >= 0; i--) {
            esperados.add(padrao(usuarioA, T0.plusSeconds(i), 25.0f));
        }
        Long empatado = padrao(usuarioA, T0, 25.0f);
        esperados.add(empatado);
        sincronizar();

        List<Long> percorridos = new ArrayList<>();
        PosicaoNoHistorico posicao = null;
        do {
            List<ChamadoHistoricoItem> pagina = consulta.listarHistorico(usuarioA, posicao, 2);
            percorridos.addAll(ids(pagina));
            ChamadoHistoricoItem ultimo = pagina.isEmpty() ? null : pagina.get(pagina.size() - 1);
            posicao = ultimo == null ? null : new PosicaoNoHistorico(ultimo.dataCriacao(), ultimo.id());
            if (pagina.size() < 2) {
                break;
            }
        } while (true);

        assertThat(percorridos).containsExactlyElementsOf(esperados);
    }

    @Test
    void naoDeveTrazerChamadosDeOutroUsuario() {
        Long deA = padrao(usuarioA, T0, 25.0f);
        padrao(usuarioB, T0.plusSeconds(1), 25.0f);
        emergencia(usuarioB, T0.plusSeconds(2));
        sincronizar();

        assertThat(ids(consulta.listarHistorico(usuarioA, null, 10))).containsExactly(deA);
        assertThat(consulta.listarHistorico(usuarioA, new PosicaoNoHistorico(T0.plusSeconds(10), 0L), 10))
                .extracting(ChamadoHistoricoItem::id).containsExactly(deA);
    }

    @Test
    void deveProjetarOcorrenciaBaixaPrecisaoEstadoEDataEmUtc() {
        Long padraoId = padrao(usuarioA, T0.plusSeconds(1), 150.0f);
        Long eiId = emergencia(usuarioA, T0);
        sincronizar();

        List<ChamadoHistoricoItem> itens = consulta.listarHistorico(usuarioA, null, 10);

        ChamadoHistoricoItem padrao = itens.get(0);
        assertThat(padrao.id()).isEqualTo(padraoId);
        assertThat(padrao.modalidade()).isEqualTo(ModalidadeChamado.PADRAO);
        assertThat(padrao.orgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(padrao.ocorrenciaId()).isEqualTo(ocorrenciaSamu);
        assertThat(padrao.ocorrenciaNome()).isEqualTo("Convulsão / Ataque epiléptico");
        assertThat(padrao.baixaPrecisao()).isTrue();
        assertThat(padrao.estado()).isEqualTo(EstadoChamado.CRIADO);
        assertThat(padrao.dataCriacao()).isEqualTo(T0.plusSeconds(1));

        ChamadoHistoricoItem ei = itens.get(1);
        assertThat(ei.id()).isEqualTo(eiId);
        assertThat(ei.modalidade()).isEqualTo(ModalidadeChamado.EMERGENCIA_IMEDIATA);
        assertThat(ei.orgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
        assertThat(ei.ocorrenciaId()).isNull();
        assertThat(ei.ocorrenciaNome()).isNull();
        assertThat(ei.baixaPrecisao()).isFalse();
    }

    @Test
    void estadoDeveRefletirAsTransicoes() {
        Long id = padrao(usuarioA, T0, 25.0f);
        sincronizar();
        Chamado chamado = chamados.buscarPorId(id).orElseThrow();
        chamado.encaminharSimulado(T0.plusSeconds(8));
        chamados.salvar(chamado);
        sincronizar();

        assertThat(consulta.listarHistorico(usuarioA, null, 10))
                .extracting(ChamadoHistoricoItem::estado).containsExactly(EstadoChamado.ENCAMINHADO_SIMULADO);
    }

    @Test
    void nomeDaOcorrenciaDeveAparecerMesmoComOCatalogoInativado() {
        padrao(usuarioA, T0, 25.0f);
        sincronizar();
        jdbc.update("UPDATE ocorrencia_tipo SET ativo = FALSE WHERE id = ?", ocorrenciaSamu);

        assertThat(consulta.listarHistorico(usuarioA, null, 10))
                .extracting(ChamadoHistoricoItem::ocorrenciaNome).containsExactly("Convulsão / Ataque epiléptico");
    }

    @Test
    void usuarioSemChamadosDeveReceberListaVazia() {
        assertThat(consulta.listarHistorico(usuarioA, null, 10)).isEmpty();
    }

    // Y5: a consulta percorre idx_chamado_historico na ordem do índice, sem ordenação extra.
    @Test
    void consultaDeveUsarOIndiceDoHistoricoSemFilesort() {
        for (int i = 0; i < 40; i++) {
            padrao(usuarioA, T0.plusSeconds(i), 25.0f);
            padrao(usuarioB, T0.plusSeconds(i), 25.0f);
        }
        // Sem ANALYZE TABLE: no MySQL ele faz commit implícito e romperia o rollback do teste.
        sincronizar();
        LocalDateTime corte = LocalDateTime.ofInstant(T0.plusSeconds(20), ZoneOffset.UTC);

        Map<String, Object> inicio = planoDoChamado(jdbc.queryForList("EXPLAIN " + ChamadoConsultaJdbc.SQL_INICIO,
                usuarioA, 21));
        Map<String, Object> continuacao = planoDoChamado(jdbc.queryForList(
                "EXPLAIN " + ChamadoConsultaJdbc.SQL_CONTINUACAO, usuarioA, corte, corte, 0L, 21));

        for (Map<String, Object> plano : List.of(inicio, continuacao)) {
            assertThat(plano.get("key")).isEqualTo("idx_chamado_historico");
            assertThat(String.valueOf(plano.get("Extra"))).doesNotContainIgnoringCase("filesort");
        }
    }

    private static Map<String, Object> planoDoChamado(List<Map<String, Object>> plano) {
        return plano.stream().filter(linha -> "c".equals(linha.get("table"))).findFirst().orElseThrow();
    }
}
