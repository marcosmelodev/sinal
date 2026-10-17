package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.TokenJwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cadastro e login reais (BCrypt e JWT); cada teste desfaz a própria transação.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AutenticacaoControllerTest {

    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA = "senha-forte-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenJwtService tokenJwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private Long usuarioId;

    @BeforeEach
    void cadastrar() throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Maria\", \"cpf\": \"%s\", \"email\": \"%s\", \"senha\": \"%s\"}"
                                .formatted(CPF, EMAIL, SENHA)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        usuarioId = Long.valueOf(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions login(String corpo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions login(String identificador, String senha) throws Exception {
        return login("{\"identificador\": \"%s\", \"senha\": \"%s\"}".formatted(identificador, senha));
    }

    private static String semCorrelationId(String corpo) {
        return corpo.replaceAll("\"correlationId\":\"[^\"]*\"", "");
    }

    @Test
    void loginPorCpfDeveDevolverTokenBearerDe15Minutos() throws Exception {
        Instant antes = Instant.now();
        String resposta = login("529.982.247-25", SENHA)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(content().string(not(containsString(CPF))))
                .andReturn().getResponse().getContentAsString();

        String token = resposta.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertThat(tokenJwtService.validarEExtrairUsuarioId(token)).isEqualTo(usuarioId);
        Instant expiraEm = Instant.parse(resposta.replaceAll(".*\"expiraEm\":\"([^\"]+)\".*", "$1"));
        assertThat(Duration.between(antes, expiraEm)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(16));
    }

    @Test
    void loginPorEmailDeveFuncionarIndependenteDeMaiusculas() throws Exception {
        login("MARIA@Exemplo.com.br", SENHA).andExpect(status().isOk());
    }

    @Test
    void senhaErradaEUsuarioInexistenteDevemTerRespostasIdenticas() throws Exception {
        String senhaErrada = login(CPF, "senha-errada").andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("Dados incorretos."))
                .andReturn().getResponse().getContentAsString();
        String inexistente = login("11144477735", SENHA).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String identificadorInvalido = login("nao-e-cpf", SENHA).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(semCorrelationId(inexistente)).isEqualTo(semCorrelationId(senhaErrada));
        assertThat(semCorrelationId(identificadorInvalido)).isEqualTo(semCorrelationId(senhaErrada));
    }

    @Test
    void camposAusentesDevemSerCredenciaisInvalidas() throws Exception {
        login("{}").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void contaInativaSoDeveSerReveladaComSenhaCorreta() throws Exception {
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", usuarioId);
        // Mesma transação do cadastro: descarta o usuário em cache para o login ler o banco.
        entityManager.clear();

        login(CPF, SENHA).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONTA_INATIVA"));
        login(CPF, "senha-errada").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }
}
