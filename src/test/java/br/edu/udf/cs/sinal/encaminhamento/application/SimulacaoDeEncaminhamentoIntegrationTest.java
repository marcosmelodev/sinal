package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.RelogioAjustavel;
import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.TokenJwtService;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.EncaminhamentoGateway;
import br.edu.udf.cs.sinal.plataforma.config.AgendamentoConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Fluxo completo com MySQL real e relógio ajustável: registro -> ouvinte AFTER_COMMIT -> job (avanço e
// reconciliação). Sem @Transactional: cada etapa confirma de verdade. Os dados criados são removidos ao final.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SimulacaoDeEncaminhamentoIntegrationTest.RelogioDeTeste.class})
class SimulacaoDeEncaminhamentoIntegrationTest {

    private static final Instant INICIO = Instant.parse("2031-03-10T15:00:00Z");
    private static final String CPF = "30000000116";
    private static final String DF = "{\"latitude\": -15.7939, \"longitude\": -47.8828, \"precisao\": 25.0, "
            + "\"timestamp\": \"2031-03-10T14:59:59Z\"}";
    private static final String SAO_PAULO = "{\"latitude\": -23.5505, \"longitude\": -46.6333, \"precisao\": 25.0, "
            + "\"timestamp\": \"2031-03-10T14:59:59Z\"}";

    @TestConfiguration
    static class RelogioDeTeste {

        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel(INICIO);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenJwtService tokenJwtService;

    private String bearer(Long usuarioId) {
        return "Bearer " + tokenJwtService.emitir(usuarioId).accessToken();
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private AvancoDeEncaminhamentoJob job;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private ApplicationContext contexto;

    // Spy só para simular a falha do gateway em um teste; nos demais se comporta como o adapter simulado.
    @MockitoSpyBean
    private EncaminhamentoGateway encaminhamentoGateway;

    private Long usuarioId;

    @BeforeEach
    void preparar() {
        relogio.definir(INICIO);
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", CPF, "simulacao@exemplo.com", "hash", true, Timestamp.from(INICIO));
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, CPF);
    }

    @AfterEach
    void removerDados() {
        jdbc.update("DELETE e FROM encaminhamento e JOIN chamado c ON c.id = e.chamado_id WHERE c.usuario_id = ?",
                usuarioId);
        jdbc.update("DELETE t FROM chamado_transicao t JOIN chamado c ON c.id = t.chamado_id WHERE c.usuario_id = ?",
                usuarioId);
        jdbc.update("DELETE FROM chamado WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private Long registrarEmergencia(String chave, String localizacao) throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/chamados/emergencia-imediata")
                        .contentType(MediaType.APPLICATION_JSON).header("Authorization", bearer(usuarioId))
                        .header("Idempotency-Key", chave).content("{\"localizacao\": " + localizacao + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private Map<String, Object> encaminhamento(Long chamadoId) {
        List<Map<String, Object>> linhas = jdbc.queryForList(
                "SELECT status, jurisdicao_codigo, central_codigo, proxima_transicao_em FROM encaminhamento "
                        + "WHERE chamado_id = ?", chamadoId);
        return linhas.isEmpty() ? null : linhas.get(0);
    }

    private static Instant utc(Object datetime) {
        return datetime == null ? null : ((LocalDateTime) datetime).toInstant(ZoneOffset.UTC);
    }

    private String estado(Long chamadoId) {
        return jdbc.queryForObject("SELECT estado FROM chamado WHERE id = ?", String.class, chamadoId);
    }

    private int transicoes(Long chamadoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM chamado_transicao WHERE chamado_id = ?", Integer.class,
                chamadoId);
    }

    @Test
    void jobAgendadoDeveEstarDesligadoNaSuiteDeTestes() {
        assertThat(contexto.getBeanNamesForType(AgendamentoConfig.class)).isEmpty();
    }

    @Test
    void registroDeveCriarOEncaminhamentoAgendadoComACentralFicticiaDoDf() throws Exception {
        Long chamadoId = registrarEmergencia("sim-1", DF);

        Map<String, Object> agendado = encaminhamento(chamadoId);
        assertThat(agendado.get("status")).isEqualTo("AGENDADO");
        assertThat(agendado.get("jurisdicao_codigo")).isEqualTo("JURISDICAO-FICTICIA-DF");
        assertThat(agendado.get("central_codigo")).isEqualTo("CENTRAL-FICTICIA-DF-PM");
        assertThat(utc(agendado.get("proxima_transicao_em"))).isEqualTo(INICIO.plusSeconds(8));
    }

    @Test
    void registroForaDoDfDeveCriarOEncaminhamentoSemJurisdicaoNemCentral() throws Exception {
        Long chamadoId = registrarEmergencia("sim-sp", SAO_PAULO);

        Map<String, Object> agendado = encaminhamento(chamadoId);
        assertThat(agendado.get("status")).isEqualTo("AGENDADO");
        assertThat(agendado.get("jurisdicao_codigo")).isNull();
        assertThat(agendado.get("central_codigo")).isNull();
    }

    @Test
    void jobDeveConduzirASimulacaoAteOEncerramento() throws Exception {
        Long chamadoId = registrarEmergencia("sim-1", DF);

        relogio.avancar(Duration.ofSeconds(7));
        job.executar();
        assertThat(estado(chamadoId)).isEqualTo("CRIADO");

        relogio.avancar(Duration.ofSeconds(1));
        job.executar();
        assertThat(estado(chamadoId)).isEqualTo("ENCAMINHADO_SIMULADO");
        assertThat(transicoes(chamadoId)).isEqualTo(2);
        Map<String, Object> reagendado = encaminhamento(chamadoId);
        assertThat(reagendado.get("status")).isEqualTo("AGENDADO");
        assertThat(utc(reagendado.get("proxima_transicao_em"))).isEqualTo(INICIO.plusSeconds(28));

        relogio.avancar(Duration.ofSeconds(19));
        job.executar();
        assertThat(estado(chamadoId)).isEqualTo("ENCAMINHADO_SIMULADO");

        relogio.avancar(Duration.ofSeconds(1));
        job.executar();
        assertThat(estado(chamadoId)).isEqualTo("ENCERRADO");
        assertThat(transicoes(chamadoId)).isEqualTo(3);
        Map<String, Object> concluido = encaminhamento(chamadoId);
        assertThat(concluido.get("status")).isEqualTo("CONCLUIDO");
        assertThat(concluido.get("proxima_transicao_em")).isNull();

        relogio.avancar(Duration.ofMinutes(5));
        job.executar();
        assertThat(transicoes(chamadoId)).isEqualTo(3);

        mockMvc.perform(get("/api/v1/chamados").header("Authorization", bearer(usuarioId)))
                .andExpect(jsonPath("$.itens[0].estado").value("ENCERRADO"));
    }

    @Test
    void reconciliacaoDeveCriarOEncaminhamentoDeChamadoSemEventoAposACarencia() {
        // Gravado direto pelo repositório: nenhum ChamadoRegistrado é publicado (evento perdido).
        Chamado chamado = chamadoRepository.salvarNovo(Chamado.registrarEmergenciaImediata(usuarioId,
                new Localizacao(-15.7939, -47.8828, 25.0f, INICIO), 100.0f, INICIO), "sem-evento");

        job.executar();
        assertThat(encaminhamento(chamado.getId())).isNull();

        relogio.avancar(Duration.ofSeconds(31));
        job.executar();
        Map<String, Object> agendado = encaminhamento(chamado.getId());
        assertThat(agendado.get("status")).isEqualTo("AGENDADO");
        assertThat(utc(agendado.get("proxima_transicao_em"))).isEqualTo(INICIO.plusSeconds(39));
    }

    @Test
    void falhaNoOuvinteNaoDeveAfetarORegistroEAReconciliacaoDeveRecuperar() throws Exception {
        doThrow(new IllegalStateException("simulação indisponível"))
                .doCallRealMethod()
                .when(encaminhamentoGateway).encaminhar(any(), any());

        Long chamadoId = registrarEmergencia("sim-falha", DF);
        assertThat(encaminhamento(chamadoId)).isNull();
        assertThat(estado(chamadoId)).isEqualTo("CRIADO");

        relogio.avancar(Duration.ofSeconds(31));
        job.executar();
        assertThat(encaminhamento(chamadoId).get("status")).isEqualTo("AGENDADO");
    }
}
