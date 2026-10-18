package br.edu.udf.cs.sinal.plataforma.security;

import br.edu.udf.cs.sinal.RelogioAjustavel;
import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.TokenJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cadeia de segurança real (Etapa 4 §20) com relógio ajustável para a expiração do token.
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SegurancaApiTest.RelogioDeTeste.class})
@Transactional
class SegurancaApiTest {

    private static final Instant INICIO = Instant.parse("2031-09-01T12:00:00Z");

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

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private JdbcTemplate jdbc;

    private Long ativo;
    private Long inativo;

    @BeforeEach
    void preparar() {
        relogio.definir(INICIO);
        ativo = inserirUsuario("30000000388", "seguranca-ativo@exemplo.com", true);
        inativo = inserirUsuario("52998224725", "seguranca-inativo@exemplo.com", false);
    }

    private Long inserirUsuario(String cpf, String email, boolean usuarioAtivo) {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", cpf, email, "hash", usuarioAtivo, Timestamp.from(INICIO));
        return jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, cpf);
    }

    private String token(Long usuarioId) {
        return tokenJwtService.emitir(usuarioId).accessToken();
    }

    private static void assertNaoAutenticado(ResultActions resultado, String code) throws Exception {
        resultado.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rotasProtegidasSemTokenDevemRetornar401() throws Exception {
        List<MockHttpServletRequestBuilder> protegidas = List.of(get("/api/v1/chamados"),
                post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/contatos-confianca"), get("/api/v1/ocorrencias").param("orgao", "SAMU"),
                get("/api/v1/usuarios"));

        for (MockHttpServletRequestBuilder requisicao : protegidas) {
            assertNaoAutenticado(mockMvc.perform(requisicao), "NAO_AUTENTICADO");
        }
    }

    @Test
    void cadastroELoginDevemSerPublicos() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void tokenValidoDeUsuarioAtivoDeveAcessarAsRotasProtegidas() throws Exception {
        mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", "Bearer " + token(ativo)))
                .andExpect(status().isOk());
    }

    @Test
    void cabecalhoAuthorizationMalFormadoDeveRetornar401() throws Exception {
        for (String valor : List.of("Bearer", "Bearer abc.def.ghi", "Token " + token(ativo), token(ativo))) {
            assertNaoAutenticado(mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", valor)),
                    "NAO_AUTENTICADO");
        }
    }

    @Test
    void tokenComAssinaturaAlteradaDeveRetornar401() throws Exception {
        String token = token(ativo);
        String adulterado = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertNaoAutenticado(mockMvc.perform(get("/api/v1/contatos-confianca")
                .header("Authorization", "Bearer " + adulterado)), "NAO_AUTENTICADO");
    }

    @Test
    void tokenExpiradoDeveRetornarTokenExpirado() throws Exception {
        String token = token(ativo);

        relogio.avancar(Duration.ofMinutes(15));
        mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        relogio.avancar(Duration.ofSeconds(1));
        assertNaoAutenticado(mockMvc.perform(get("/api/v1/contatos-confianca")
                .header("Authorization", "Bearer " + token)), "TOKEN_EXPIRADO");
    }

    @Test
    void respostaDeSegurancaNaoDeveExporOMotivoTecnico() throws Exception {
        String token = token(ativo);
        relogio.avancar(Duration.ofHours(1));

        mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", "Bearer " + token))
                .andExpect(content().string(not(containsString("Jwt"))))
                .andExpect(content().string(not(containsString("expired"))));
    }

    @Test
    void cabecalhoProvisorioNaoDeveAutenticar() throws Exception {
        assertNaoAutenticado(mockMvc.perform(get("/api/v1/contatos-confianca")
                .header("X-Usuario-Id", String.valueOf(ativo))), "NAO_AUTENTICADO");
    }

    @Test
    void tokenDeUsuarioInativoDeveRetornar403ContaInativa() throws Exception {
        mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", "Bearer " + token(inativo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTA_INATIVA"));
    }

    @Test
    void cabecalhosDeSegurancaDevemEstarPresentes() throws Exception {
        mockMvc.perform(get("/api/v1/contatos-confianca").header("Authorization", "Bearer " + token(ativo)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    // ---- CORS (front-end local em http://localhost:5173) ----

    @Test
    void preflightDoFrontEndLocalDeveSerAceitoSemToken() throws Exception {
        mockMvc.perform(options("/api/v1/contatos-confianca")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void preflightDeOrigemNaoPermitidaDeveSerRecusado() throws Exception {
        mockMvc.perform(options("/api/v1/contatos-confianca")
                        .header("Origin", "http://site-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void requisicaoDoFrontEndComTokenDeveTerCabecalhoCors() throws Exception {
        mockMvc.perform(get("/api/v1/contatos-confianca").header("Origin", "http://localhost:5173")
                        .header("Authorization", "Bearer " + token(ativo)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
