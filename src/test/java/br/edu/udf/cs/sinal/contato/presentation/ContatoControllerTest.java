package br.edu.udf.cs.sinal.contato.presentation;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.TokenJwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sem @Transactional de propósito: cada requisição usa a transação aberta pelo próprio serviço, o que comprova
// que as alterações satisfazem o bloqueio MANDATORY do repositório. Os dados criados são removidos ao final.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContatoControllerTest {

    private static final String URL = "/api/v1/contatos-confianca";
    private static final List<String> CPFS = List.of("10000000604", "10000000280", "10000001090");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenJwtService tokenJwtService;

    private String bearer(Long usuarioId) {
        return "Bearer " + tokenJwtService.emitir(usuarioId).accessToken();
    }

    @Autowired
    private JdbcTemplate jdbc;

    private Long usuarioA;
    private Long usuarioB;
    private Long usuarioInativo;

    @BeforeEach
    void criarUsuarios() {
        usuarioA = inserirUsuario(CPFS.get(0), "contatos-a@exemplo.com", true);
        usuarioB = inserirUsuario(CPFS.get(1), "contatos-b@exemplo.com", true);
        usuarioInativo = inserirUsuario(CPFS.get(2), "contatos-inativo@exemplo.com", false);
    }

    @AfterEach
    void removerDados() {
        for (String cpf : CPFS) {
            jdbc.update("DELETE FROM contato_confianca WHERE usuario_id IN (SELECT id FROM usuario WHERE cpf = ?)", cpf);
            jdbc.update("DELETE FROM usuario WHERE cpf = ?", cpf);
        }
    }

    private Long inserirUsuario(String cpf, String email, boolean ativo) {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", cpf, email, "hash", ativo, Timestamp.from(Instant.parse("2026-10-17T12:00:00Z")));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, cpf);
    }

    private static String corpo(String nome, String telefone) {
        return "{\"nome\": %s, \"telefone\": %s}".formatted(json(nome), json(telefone));
    }

    private static String json(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder requisicao, Long usuarioId) {
        return requisicao.header("Authorization", bearer(usuarioId)).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions adicionar(Long usuarioId, String nome, String telefone) throws Exception {
        return mockMvc.perform(como(post(URL), usuarioId).content(corpo(nome, telefone)));
    }

    private Long adicionarERetornarId(Long usuarioId, String nome) throws Exception {
        MockHttpServletResponse resposta = adicionar(usuarioId, nome, "61999998888")
                .andExpect(status().isCreated()).andReturn().getResponse();
        return Long.valueOf(resposta.getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private static void assertProblema(ResultActions resultado, int status, String code) throws Exception {
        resultado.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void adicionarDeveRetornar201ComLocationETelefoneNormalizado() throws Exception {
        adicionar(usuarioA, "  Ana Souza ", "(61) 99999-8888")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/contatos-confianca/\\d+$")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.telefone").value("61999998888"));
    }

    @Test
    void listarDeveRetornarOsContatosDoUsuarioNaOrdem() throws Exception {
        adicionarERetornarId(usuarioA, "Ana");
        adicionarERetornarId(usuarioA, "Bruno");
        adicionarERetornarId(usuarioB, "Contato de B");

        mockMvc.perform(como(get(URL), usuarioA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[1].nome").value("Bruno"));
    }

    @Test
    void listarSemContatosDeveRetornarListaVazia() throws Exception {
        mockMvc.perform(como(get(URL), usuarioA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void editarDeveRetornar200PreservandoOId() throws Exception {
        Long id = adicionarERetornarId(usuarioA, "Ana");

        mockMvc.perform(como(put(URL + "/" + id), usuarioA).content(corpo("Ana Lima", "+55 61 3333-4444")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nome").value("Ana Lima"))
                .andExpect(jsonPath("$.telefone").value("6133334444"));
    }

    @Test
    void removerDeveRetornar204ESumirDaListagem() throws Exception {
        Long id = adicionarERetornarId(usuarioA, "Ana");
        adicionarERetornarId(usuarioA, "Bruno");

        mockMvc.perform(como(delete(URL + "/" + id), usuarioA))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(como(get(URL), usuarioA))
                .andExpect(jsonPath("$[*].nome", containsInAnyOrder("Bruno")));
    }

    @Test
    void sextoContatoDeveRetornar422() throws Exception {
        for (int i = 1; i <= 5; i++) {
            adicionarERetornarId(usuarioA, "Contato " + i);
        }

        assertProblema(adicionar(usuarioA, "Sexto", "61999998888"), 422, "LIMITE_CONTATOS_EXCEDIDO");
        mockMvc.perform(como(get(URL), usuarioA)).andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void camposEmBrancoDevemRetornar400() throws Exception {
        ResultActions resultado = adicionar(usuarioA, " ", null);

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("nome", "telefone")))
                .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder("CAMPO_OBRIGATORIO", "CAMPO_OBRIGATORIO")));
    }

    @Test
    void telefoneInvalidoDeveRetornar400NoCampoTelefoneSemRepetirOValor() throws Exception {
        ResultActions resultado = adicionar(usuarioA, "Ana", "61-12345");

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("telefone"))
                .andExpect(jsonPath("$.errors[0].code").value("CONTATO_TELEFONE_INVALIDO"))
                .andExpect(content().string(not(containsString("61-12345"))));
    }

    @Test
    void contatoDeOutroUsuarioDeveRetornar404ESerPreservado() throws Exception {
        Long contatoDeB = adicionarERetornarId(usuarioB, "Contato de B");

        assertProblema(mockMvc.perform(como(put(URL + "/" + contatoDeB), usuarioA).content(corpo("Invasor", "61999998888"))),
                404, "RECURSO_NAO_ENCONTRADO");
        assertProblema(mockMvc.perform(como(delete(URL + "/" + contatoDeB), usuarioA)), 404, "RECURSO_NAO_ENCONTRADO");
        mockMvc.perform(como(get(URL), usuarioB))
                .andExpect(jsonPath("$[0].nome").value("Contato de B"));
    }

    @Test
    void contatoInexistenteDeveRetornar404() throws Exception {
        assertProblema(mockMvc.perform(como(delete(URL + "/999999"), usuarioA)), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void idNaoNumericoDeveRetornar400() throws Exception {
        assertProblema(mockMvc.perform(como(delete(URL + "/abc"), usuarioA)), 400, "VALIDACAO_INVALIDA");
    }

    @Test
    void semTokenDeveRetornar401() throws Exception {
        assertProblema(mockMvc.perform(get(URL)), 401, "NAO_AUTENTICADO");
    }

    // O cabeçalho provisório da Etapa 8.3 não autentica mais ninguém.
    @Test
    void cabecalhoProvisorioDeveSerIgnorado() throws Exception {
        assertProblema(mockMvc.perform(get(URL).header("X-Usuario-Id", String.valueOf(usuarioA))),
                401, "NAO_AUTENTICADO");
    }

    @Test
    void tokenInvalidoOuDeUsuarioInexistenteDeveRetornar401EInativo403() throws Exception {
        assertProblema(mockMvc.perform(get(URL).header("Authorization", "Bearer abc")), 401, "NAO_AUTENTICADO");
        assertProblema(mockMvc.perform(get(URL).header("Authorization", bearer(999_999L))), 401, "NAO_AUTENTICADO");
        assertProblema(mockMvc.perform(como(post(URL), usuarioInativo).content(corpo("Ana", "61999998888"))),
                403, "CONTA_INATIVA");
    }

    @Test
    void naoAutenticadoNaoDeveGravarNada() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corpo("Ana", "61999998888")))
                .andExpect(status().isUnauthorized());

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM contato_confianca WHERE usuario_id IN (?, ?, ?)",
                Integer.class, usuarioA, usuarioB, usuarioInativo);
        assertThat(total).isZero();
    }
}
