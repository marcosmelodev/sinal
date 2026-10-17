package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.chamado.application.ChamadoRegistrado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sem @Transactional de propósito: o serviço abre as próprias transações (registro e replay) e o evento
// ChamadoRegistrado só é entregue após o commit real. Os dados criados são removidos ao final.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ChamadoControllerTest.OuvinteDeTeste.class})
class ChamadoControllerTest {

    private static final String URL = "/api/v1/chamados";
    private static final String URL_EI = "/api/v1/chamados/emergencia-imediata";
    private static final List<String> CPFS = List.of("20000000108", "20000000299");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventosRecebidos eventos;

    // Spy só para forçar a corrida; nos demais testes se comporta como o repositório real.
    @MockitoSpyBean
    private ChamadoRepository chamadoRepository;

    private Long usuarioA;
    private Long usuarioB;

    // Ouvinte AFTER_COMMIT: registra o evento e se o Chamado já estava visível (commit concluído) naquele momento.
    record EventoRecebido(ChamadoRegistrado evento, boolean chamadoJaCommitado) {
    }

    static class EventosRecebidos extends CopyOnWriteArrayList<EventoRecebido> {
    }

    @TestConfiguration
    static class OuvinteDeTeste {

        @Bean
        EventosRecebidos eventosRecebidos() {
            return new EventosRecebidos();
        }

        @Bean
        Ouvinte ouvinte(EventosRecebidos recebidos, JdbcTemplate jdbc) {
            return new Ouvinte(recebidos, jdbc);
        }
    }

    static class Ouvinte {

        private final EventosRecebidos recebidos;
        private final JdbcTemplate jdbc;

        Ouvinte(EventosRecebidos recebidos, JdbcTemplate jdbc) {
            this.recebidos = recebidos;
            this.jdbc = jdbc;
        }

        @TransactionalEventListener
        void aoRegistrar(ChamadoRegistrado evento) {
            Integer visiveis = jdbc.queryForObject("SELECT COUNT(*) FROM chamado WHERE id = ?", Integer.class,
                    evento.chamadoId());
            recebidos.add(new EventoRecebido(evento, visiveis != null && visiveis == 1));
        }
    }

    @BeforeEach
    void criarUsuarios() {
        eventos.clear();
        usuarioA = inserirUsuario(CPFS.get(0), "chamados-a@exemplo.com");
        usuarioB = inserirUsuario(CPFS.get(1), "chamados-b@exemplo.com");
    }

    @AfterEach
    void removerDados() {
        for (String cpf : CPFS) {
            jdbc.update("""
                    DELETE e FROM encaminhamento e JOIN chamado c ON c.id = e.chamado_id
                    JOIN usuario u ON u.id = c.usuario_id WHERE u.cpf = ?""", cpf);
            jdbc.update("""
                    DELETE t FROM chamado_transicao t JOIN chamado c ON c.id = t.chamado_id
                    JOIN usuario u ON u.id = c.usuario_id WHERE u.cpf = ?""", cpf);
            jdbc.update("DELETE c FROM chamado c JOIN usuario u ON u.id = c.usuario_id WHERE u.cpf = ?", cpf);
            jdbc.update("DELETE FROM usuario WHERE cpf = ?", cpf);
        }
    }

    private Long inserirUsuario(String cpf, String email) {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", cpf, email, "hash", true, Timestamp.from(Instant.parse("2026-10-17T12:00:00Z")));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, cpf);
    }

    private Long ocorrencia(String codigo) {
        return jdbc.queryForObject("SELECT id FROM ocorrencia_tipo WHERE codigo = ?", Long.class, codigo);
    }

    private static String localizacao(double precisao) {
        return """
                {"latitude": -15.7939, "longitude": -47.8828, "precisao": %s, "timestamp": "2026-10-17T11:59:58.123Z"}"""
                .formatted(precisao);
    }

    private String corpoPadrao(String orgao, Long ocorrenciaId, String detalhe, double precisao) {
        String detalheJson = detalhe == null ? "null" : "\"" + detalhe + "\"";
        return """
                {"orgao": "%s", "ocorrenciaId": %s, "detalheAdicional": %s, "localizacao": %s}"""
                .formatted(orgao, ocorrenciaId, detalheJson, localizacao(precisao));
    }

    private ResultActions enviar(String url, Long usuarioId, String chave, String corpo) throws Exception {
        var requisicao = post(url).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .header("X-Usuario-Id", String.valueOf(usuarioId));
        if (chave != null) {
            requisicao.header("Idempotency-Key", chave);
        }
        return mockMvc.perform(requisicao);
    }

    private int linhas(String tabela, Long usuarioId) {
        String sql = tabela.equals("chamado")
                ? "SELECT COUNT(*) FROM chamado WHERE usuario_id = ?"
                : "SELECT COUNT(*) FROM " + tabela + " t JOIN chamado c ON c.id = t.chamado_id WHERE c.usuario_id = ?";
        return jdbc.queryForObject(sql, Integer.class, usuarioId);
    }

    private static void assertProblema(ResultActions resultado, int status, String code) throws Exception {
        resultado.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void registroPadraoComDetalheDeveRetornar201EGravarChamadoETransicao() throws Exception {
        Long ocorrencia = ocorrencia("SAMU_CONVULSAO");

        enviar(URL, usuarioA, "chave-1", corpoPadrao("SAMU", ocorrencia, "  Pessoa no 2º andar  ", 25.5))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/chamados/\\d+$")))
                .andExpect(jsonPath("$.codigoSimulacao", matchesPattern("#SIM-\\d+")))
                .andExpect(jsonPath("$.modalidade").value("PADRAO"))
                .andExpect(jsonPath("$.orgao").value("SAMU"))
                .andExpect(jsonPath("$.ocorrenciaId").value(ocorrencia))
                .andExpect(jsonPath("$.detalheAdicional").value("Pessoa no 2º andar"))
                .andExpect(jsonPath("$.localizacao.latitude").value(-15.7939))
                .andExpect(jsonPath("$.baixaPrecisao").value(false))
                .andExpect(jsonPath("$.estado").value("CRIADO"))
                .andExpect(jsonPath("$.modoEncaminhamento").value("SIMULADO"));

        assertThat(linhas("chamado", usuarioA)).isEqualTo(1);
        assertThat(linhas("chamado_transicao", usuarioA)).isEqualTo(1);
        // Etapa 8.5: o ouvinte de ChamadoRegistrado cria o encaminhamento simulado após o commit.
        assertThat(linhas("encaminhamento", usuarioA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT e.status FROM encaminhamento e JOIN chamado c ON c.id = e.chamado_id "
                + "WHERE c.usuario_id = ?", String.class, usuarioA)).isEqualTo("AGENDADO");
    }

    @Test
    void registroPadraoSemDetalheDeveRetornar201() throws Exception {
        enviar(URL, usuarioA, "chave-1", corpoPadrao("CORPO_DE_BOMBEIROS", ocorrencia("CBM_OUTRO"), null, 25.5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalheAdicional").doesNotExist());
    }

    @Test
    void emergenciaImediataDeveSerPmSemOcorrenciaNemDetalheMesmoSeEnviados() throws Exception {
        enviar(URL_EI, usuarioA, "chave-ei", """
                {"orgao": "SAMU", "ocorrenciaId": 1, "detalheAdicional": "ignorado", "localizacao": %s}"""
                .formatted(localizacao(25.5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modalidade").value("EMERGENCIA_IMEDIATA"))
                .andExpect(jsonPath("$.orgao").value("POLICIA_MILITAR"))
                .andExpect(jsonPath("$.ocorrenciaId").doesNotExist())
                .andExpect(jsonPath("$.detalheAdicional").doesNotExist());
    }

    @Test
    void baixaPrecisaoDeveSerSinalizadaSemBloquear() throws Exception {
        enviar(URL_EI, usuarioA, "chave-ei", "{\"localizacao\": " + localizacao(150.0) + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baixaPrecisao").value(true));
    }

    @Test
    void mesmaChaveEMesmoCorpoDevemRetornar200ComOMesmoChamado() throws Exception {
        String corpo = corpoPadrao("SAMU", ocorrencia("SAMU_CONVULSAO"), "Detalhe", 25.5);
        String primeiro = enviar(URL, usuarioA, "chave-1", corpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = primeiro.replaceAll(".*\"id\":(\\d+).*", "$1");

        enviar(URL, usuarioA, "chave-1", corpo)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(Long.valueOf(id)));

        assertThat(linhas("chamado", usuarioA)).isEqualTo(1);
        assertThat(eventos).hasSize(1);
    }

    // Corrida real: a checagem prévia não enxerga o Chamado já gravado; o UNIQUE do MySQL decide e o serviço
    // relê em nova transação e responde como replay (Etapa 4 §17), pelo nome real da restrição.
    @Test
    void corridaNaChaveDeveSerResolvidaPeloBancoEResponderComoReplay() throws Exception {
        String corpo = "{\"localizacao\": " + localizacao(25.5) + "}";
        String primeiro = enviar(URL_EI, usuarioA, "chave-corrida", corpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = primeiro.replaceAll(".*\"id\":(\\d+).*", "$1");
        doReturn(Optional.empty()).doCallRealMethod()
                .when(chamadoRepository).buscarPorUsuarioEChaveIdempotencia(anyLong(), anyString());

        enviar(URL_EI, usuarioA, "chave-corrida", corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.valueOf(id)));

        assertThat(linhas("chamado", usuarioA)).isEqualTo(1);
        assertThat(eventos).hasSize(1);
    }

    @Test
    void mesmaChaveComCorpoDiferenteDeveRetornar409() throws Exception {
        Long ocorrencia = ocorrencia("SAMU_CONVULSAO");
        enviar(URL, usuarioA, "chave-1", corpoPadrao("SAMU", ocorrencia, "Detalhe", 25.5))
                .andExpect(status().isCreated());

        assertProblema(enviar(URL, usuarioA, "chave-1", corpoPadrao("SAMU", ocorrencia, "Outro detalhe", 25.5)),
                409, "IDEMPOTENCIA_DIVERGENTE");
        assertThat(linhas("chamado", usuarioA)).isEqualTo(1);
    }

    @Test
    void mesmaChaveEntreFluxoPadraoEEmergenciaImediataDeveRetornar409() throws Exception {
        enviar(URL_EI, usuarioA, "chave-1", "{\"localizacao\": " + localizacao(25.5) + "}")
                .andExpect(status().isCreated());

        assertProblema(enviar(URL, usuarioA, "chave-1", corpoPadrao("SAMU", ocorrencia("SAMU_CONVULSAO"), null, 25.5)),
                409, "IDEMPOTENCIA_DIVERGENTE");
    }

    @Test
    void mesmaChaveParaUsuariosDiferentesDeveGerarDoisChamados() throws Exception {
        String corpo = "{\"localizacao\": " + localizacao(25.5) + "}";

        enviar(URL_EI, usuarioA, "chave-1", corpo).andExpect(status().isCreated());
        enviar(URL_EI, usuarioB, "chave-1", corpo).andExpect(status().isCreated());

        assertThat(linhas("chamado", usuarioA)).isEqualTo(1);
        assertThat(linhas("chamado", usuarioB)).isEqualTo(1);
    }

    @Test
    void chaveDeIdempotenciaAusenteOuInvalidaDeveRetornar400() throws Exception {
        String corpo = "{\"localizacao\": " + localizacao(25.5) + "}";

        assertProblema(enviar(URL_EI, usuarioA, null, corpo), 400, "VALIDACAO_INVALIDA");
        enviar(URL_EI, usuarioA, null, corpo)
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("CAMPO_OBRIGATORIO"));
        enviar(URL_EI, usuarioA, "chave com espaço", corpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALOR_INVALIDO"));
        assertThat(linhas("chamado", usuarioA)).isZero();
    }

    @Test
    void ocorrenciaDeOutroOrgaoInativaOuInexistenteDeveRetornar400() throws Exception {
        Long ocorrenciaDoSamu = ocorrencia("SAMU_CONVULSAO");

        for (Long ocorrencia : List.of(ocorrenciaDoSamu, 999_999L)) {
            ResultActions resultado = enviar(URL, usuarioA, "chave-" + ocorrencia,
                    corpoPadrao("POLICIA_MILITAR", ocorrencia, null, 25.5));
            assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
            resultado.andExpect(jsonPath("$.errors[0].field").value("ocorrenciaId"))
                    .andExpect(jsonPath("$.errors[0].code").value("OCORRENCIA_INVALIDA"));
        }
        assertThat(linhas("chamado", usuarioA)).isZero();
    }

    @Test
    void camposAusentesDevemRetornar400ComOsCampos() throws Exception {
        ResultActions resultado = enviar(URL, usuarioA, "chave-1", """
                {"localizacao": {"latitude": -15.79}}""");

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("orgao", "ocorrenciaId",
                "localizacao.longitude", "localizacao.precisao", "localizacao.timestamp")));
    }

    @Test
    void localizacaoForaDaFaixaDeveRetornar400NoCampoSemRepetirOValor() throws Exception {
        ResultActions resultado = enviar(URL_EI, usuarioA, "chave-1", """
                {"localizacao": {"latitude": 91.123456, "longitude": -47.88, "precisao": 25.0,
                 "timestamp": "2026-10-17T11:59:58Z"}}""");

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("localizacao.latitude"))
                .andExpect(jsonPath("$.errors[0].code").value("LOCALIZACAO_LATITUDE_INVALIDA"))
                .andExpect(content().string(not(containsString("91.123456"))));
    }

    @Test
    void precisaoNegativaDeveRetornar400() throws Exception {
        assertProblema(enviar(URL_EI, usuarioA, "chave-1", "{\"localizacao\": " + localizacao(-1.0) + "}"),
                400, "VALIDACAO_INVALIDA");
    }

    @Test
    void detalheAcimaDe500CaracteresDeveRetornar400NoCampoDetalhe() throws Exception {
        ResultActions resultado = enviar(URL, usuarioA, "chave-1",
                corpoPadrao("SAMU", ocorrencia("SAMU_CONVULSAO"), "a".repeat(501), 25.5));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("detalheAdicional"));
    }

    @Test
    void semAutenticacaoDeveRetornar401() throws Exception {
        assertProblema(mockMvc.perform(post(URL_EI).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "chave-1").content("{\"localizacao\": " + localizacao(25.5) + "}")),
                401, "NAO_AUTENTICADO");
    }

    @Test
    void eventoDeveSerEntregueSomenteAposOCommitEUmaVezPorRegistroNovo() throws Exception {
        String corpo = "{\"localizacao\": " + localizacao(25.5) + "}";
        String resposta = enviar(URL_EI, usuarioA, "chave-1", corpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = Long.valueOf(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        enviar(URL_EI, usuarioA, "chave-1", corpo).andExpect(status().isOk());
        enviar(URL_EI, usuarioA, null, corpo).andExpect(status().isBadRequest());

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).evento().chamadoId()).isEqualTo(id);
        assertThat(eventos.get(0).chamadoJaCommitado()).isTrue();
    }

    // ---- Histórico (Etapa 8.4b) ----

    private Long registrarEmergencia(Long usuarioId, String chave) throws Exception {
        String resposta = enviar(URL_EI, usuarioId, chave, "{\"localizacao\": " + localizacao(25.5) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.valueOf(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions historico(Long usuarioId, String parametros) throws Exception {
        return mockMvc.perform(get(URL + parametros).header("X-Usuario-Id", String.valueOf(usuarioId)));
    }

    private static List<Long> idsDe(String json) {
        List<Long> ids = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+),\"codigoSimulacao\"").matcher(json);
        while (m.find()) {
            ids.add(Long.valueOf(m.group(1)));
        }
        return ids;
    }

    private static String cursorDe(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"proximoCursor\":\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void historicoVazioDeveTrazerListaVaziaECursorNulo() throws Exception {
        historico(usuarioA, "")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.itens").isArray())
                .andExpect(jsonPath("$.itens").isEmpty())
                .andExpect(content().string(containsString("\"proximoCursor\":null")));
    }

    @Test
    void historicoDeveTrazerOsCamposDoItem() throws Exception {
        Long ocorrencia = ocorrencia("SAMU_CONVULSAO");
        enviar(URL, usuarioA, "chave-padrao", corpoPadrao("SAMU", ocorrencia, "Detalhe sigiloso", 150.0))
                .andExpect(status().isCreated());

        historico(usuarioA, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].codigoSimulacao", matchesPattern("#SIM-\\d+")))
                .andExpect(jsonPath("$.itens[0].modalidade").value("PADRAO"))
                .andExpect(jsonPath("$.itens[0].orgao").value("SAMU"))
                .andExpect(jsonPath("$.itens[0].ocorrencia.id").value(ocorrencia))
                .andExpect(jsonPath("$.itens[0].ocorrencia.nome").value("Convulsão / Ataque epiléptico"))
                .andExpect(jsonPath("$.itens[0].estado").value("CRIADO"))
                .andExpect(jsonPath("$.itens[0].baixaPrecisao").value(true))
                .andExpect(jsonPath("$.itens[0].dataCriacao", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.itens[0].localizacao").doesNotExist())
                .andExpect(jsonPath("$.itens[0].detalheAdicional").doesNotExist())
                .andExpect(content().string(not(containsString("Detalhe sigiloso"))))
                .andExpect(content().string(not(containsString("-15.7939"))));
    }

    @Test
    void emergenciaImediataNoHistoricoDeveTerOcorrenciaNula() throws Exception {
        registrarEmergencia(usuarioA, "chave-ei");

        historico(usuarioA, "")
                .andExpect(jsonPath("$.itens[0].modalidade").value("EMERGENCIA_IMEDIATA"))
                .andExpect(jsonPath("$.itens[0].orgao").value("POLICIA_MILITAR"))
                .andExpect(content().string(containsString("\"ocorrencia\":null")));
    }

    @Test
    void paginacaoDevePercorrerTudoSemRepetirMesmoComNovoRegistroDuranteANavegacao() throws Exception {
        List<Long> criados = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            criados.add(0, registrarEmergencia(usuarioA, "chave-" + i));
        }

        String pagina1 = historico(usuarioA, "?limite=2").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long novo = registrarEmergencia(usuarioA, "chave-durante-navegacao");
        String pagina2 = historico(usuarioA, "?limite=2&cursor=" + cursorDe(pagina1))
                .andReturn().getResponse().getContentAsString();
        String pagina3 = historico(usuarioA, "?limite=2&cursor=" + cursorDe(pagina2))
                .andReturn().getResponse().getContentAsString();

        assertThat(idsDe(pagina1)).hasSize(2);
        assertThat(idsDe(pagina2)).hasSize(2);
        assertThat(idsDe(pagina3)).hasSize(1);
        assertThat(cursorDe(pagina3)).isNull();
        List<Long> percorridos = new ArrayList<>(idsDe(pagina1));
        percorridos.addAll(idsDe(pagina2));
        percorridos.addAll(idsDe(pagina3));
        assertThat(percorridos).containsExactlyElementsOf(criados).doesNotContain(novo);

        assertThat(idsDe(historico(usuarioA, "?limite=1").andReturn().getResponse().getContentAsString()))
                .containsExactly(novo);
    }

    @Test
    void historicoNaoDeveMostrarChamadosDeOutroUsuarioNemAceitarUsuarioPelaQuery() throws Exception {
        Long deA = registrarEmergencia(usuarioA, "chave-a");
        registrarEmergencia(usuarioB, "chave-b");

        assertThat(idsDe(historico(usuarioA, "").andReturn().getResponse().getContentAsString()))
                .containsExactly(deA);
        assertThat(idsDe(historico(usuarioA, "?usuarioId=" + usuarioB).andReturn().getResponse().getContentAsString()))
                .containsExactly(deA);
    }

    @Test
    void cursorDeOutroUsuarioNaoDeveExporOsChamadosDele() throws Exception {
        registrarEmergencia(usuarioB, "chave-b1");
        registrarEmergencia(usuarioB, "chave-b2");
        String paginaDeB = historico(usuarioB, "?limite=1").andReturn().getResponse().getContentAsString();

        historico(usuarioA, "?cursor=" + cursorDe(paginaDeB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens").isEmpty());
    }

    @Test
    void limiteECursorInvalidosDevemRetornar400NoCampo() throws Exception {
        for (String limite : List.of("0", "51", "-1")) {
            ResultActions resultado = historico(usuarioA, "?limite=" + limite);
            assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
            resultado.andExpect(jsonPath("$.errors[0].field").value("limite"));
        }
        historico(usuarioA, "?limite=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limite"));
        ResultActions cursor = historico(usuarioA, "?cursor=lixo!");
        assertProblema(cursor, 400, "VALIDACAO_INVALIDA");
        cursor.andExpect(jsonPath("$.errors[0].field").value("cursor"))
                .andExpect(jsonPath("$.errors[0].code").value("VALOR_INVALIDO"));
    }

    @Test
    void historicoSemAutenticacaoDeveRetornar401() throws Exception {
        assertProblema(mockMvc.perform(get(URL)), 401, "NAO_AUTENTICADO");
    }
}
