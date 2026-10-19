package br.edu.udf.cs.sinal.libras.presentation;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.TokenJwtService;
import br.edu.udf.cs.sinal.libras.infrastructure.openai.OpenAiLibrasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// API com MySQL real e o serviço da OpenAI simulado (nenhuma chamada sai para a internet).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LibrasChatControllerTest {

    private static final String URL = "/api/v1/libras/chat";
    private static final String CPF = "70000000744";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenJwtService tokenJwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private OpenAiLibrasService openAiLibrasService;

    private Long usuarioId;

    @BeforeEach
    void preparar() {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário do chat", CPF, "chat@exemplo.com", "hash", true, Timestamp.from(Instant.now()));
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, CPF);
    }

    private ResultActions enviar(String corpo, String authorization) throws Exception {
        var requisicao = post(URL).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (authorization != null) {
            requisicao.header("Authorization", authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private String bearer() {
        return "Bearer " + tokenJwtService.emitir(usuarioId).accessToken();
    }

    private static void assertProblema(ResultActions resultado, int status, String code) throws Exception {
        resultado.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void mensagemValidaComTokenDeveRetornarARespostaDaIa() throws Exception {
        when(openAiLibrasService.responder("Meu filho se cortou")).thenReturn("Pressione o corte com um pano limpo.");

        enviar("{\"mensagem\": \"Meu filho se cortou\"}", bearer())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resposta").value("Pressione o corte com um pano limpo."));
        verify(openAiLibrasService).responder("Meu filho se cortou");
    }

    @Test
    void semTokenDeveRetornar401SemChamarAIa() throws Exception {
        assertProblema(enviar("{\"mensagem\": \"Socorro\"}", null), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(openAiLibrasService);
    }

    @Test
    void tokenInvalidoDeveRetornar401SemChamarAIa() throws Exception {
        assertProblema(enviar("{\"mensagem\": \"Socorro\"}", "Bearer token-invalido"), 401, "NAO_AUTENTICADO");
        verifyNoInteractions(openAiLibrasService);
    }

    @Test
    void contaInativaDeveRetornar403SemChamarAIa() throws Exception {
        jdbc.update("UPDATE usuario SET ativo = false WHERE id = ?", usuarioId);

        assertProblema(enviar("{\"mensagem\": \"Socorro\"}", bearer()), 403, "CONTA_INATIVA");
        verifyNoInteractions(openAiLibrasService);
    }

    @Test
    void mensagemEmBrancoDeveRetornar400() throws Exception {
        ResultActions resultado = enviar("{\"mensagem\": \"   \"}", bearer());

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("mensagem"))
                .andExpect(jsonPath("$.errors[0].code").value("CAMPO_OBRIGATORIO"));
        verifyNoInteractions(openAiLibrasService);
    }

    @Test
    void mensagemAcimaDe1000CaracteresDeveRetornar400() throws Exception {
        ResultActions resultado = enviar("{\"mensagem\": \"" + "a".repeat(1001) + "\"}", bearer());

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("mensagem"))
                .andExpect(jsonPath("$.errors[0].code").value("TAMANHO_INVALIDO"));
        verifyNoInteractions(openAiLibrasService);
    }

    @Test
    void iaIndisponivelDeveRetornar503() throws Exception {
        when(openAiLibrasService.responder(any())).thenThrow(new AplicacaoException(
                OpenAiLibrasService.CHAT_INDISPONIVEL, "O chat está indisponível no momento.", Set.of()));

        assertProblema(enviar("{\"mensagem\": \"Socorro\"}", bearer()), 503, "CHAT_INDISPONIVEL");
    }
}
