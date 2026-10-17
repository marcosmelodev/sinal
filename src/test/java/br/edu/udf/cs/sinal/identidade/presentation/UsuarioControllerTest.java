package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// API completa com MySQL real; cada teste desfaz a própria transação.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UsuarioControllerTest {

    private static final String URL = "/api/v1/usuarios";
    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";

    @Autowired
    private MockMvc mockMvc;

    private static String corpo(String nome, String cpf, String email, String senha) {
        return """
                {"nome": %s, "cpf": %s, "email": %s, "senha": %s}"""
                .formatted(json(nome), json(cpf), json(email), json(senha));
    }

    private static String json(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private ResultActions cadastrar(String corpo) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions cadastrarValido() throws Exception {
        return cadastrar(corpo("Maria da Silva", CPF, EMAIL, "senha-forte-123"));
    }

    // Formato comum a todos os erros (Etapa 4 §16).
    private static void assertProblema(ResultActions resultado, int status, String code) throws Exception {
        resultado.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void cadastroValidoDeveRetornar201ComLocationECpfMascarado() throws Exception {
        cadastrar(corpo("Maria da Silva", "529.982.247-25", "  Maria@Exemplo.COM.br ", "senha-forte-123"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/usuarios/\\d+$")))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.cpfMascarado").value("***.982.247-**"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.dataCadastro").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(content().string(not(containsString(CPF))));
    }

    @Test
    void locationDeveApontarParaOIdDevolvidoNoCorpo() throws Exception {
        MockHttpServletResponse resposta = cadastrarValido().andReturn().getResponse();
        String id = resposta.getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        assertThat(id).matches("\\d+");
        assertThat(resposta.getHeader("Location")).endsWith("/api/v1/usuarios/" + id);
    }

    @Test
    void camposExtrasNaoDevemSerAtribuidos() throws Exception {
        cadastrar("""
                {"nome": "Maria", "cpf": "52998224725", "email": "maria@exemplo.com.br",
                 "senha": "senha-forte-123", "id": 999999, "ativo": false, "senhaHash": "x"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(999999)))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(header().string("Location", not(endsWith("/999999"))));
    }

    @Test
    void camposAusentesOuEmBrancoDevemRetornar400ComErrosPorCampo() throws Exception {
        ResultActions resultado = cadastrar(corpo(" ", null, "", null));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("nome", "cpf", "email", "senha")))
                .andExpect(jsonPath("$.errors[*].code", everyItem(is("CAMPO_OBRIGATORIO"))));
    }

    @Test
    void nomeAcimaDe255CaracteresDeveRetornar400() throws Exception {
        ResultActions resultado = cadastrar(corpo("a".repeat(256), CPF, EMAIL, "senha-forte-123"));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("nome"))
                .andExpect(jsonPath("$.errors[0].code").value("TAMANHO_INVALIDO"));
    }

    @Test
    void jsonMalformadoDeveRetornar400() throws Exception {
        assertProblema(cadastrar("{\"nome\": "), 400, "VALIDACAO_INVALIDA");
    }

    @Test
    void senhaForaDaPoliticaDeveRetornar400NoCampoSenha() throws Exception {
        ResultActions resultado = cadastrar(corpo("Maria", CPF, EMAIL, "1234567"));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("senha"))
                .andExpect(jsonPath("$.errors[0].code").value("SENHA_INVALIDA"));
    }

    @Test
    void cpfComDigitoVerificadorInvalidoDeveRetornar400NoCampoCpf() throws Exception {
        ResultActions resultado = cadastrar(corpo("Maria", "52998224724", EMAIL, "senha-forte-123"));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("cpf"))
                .andExpect(jsonPath("$.errors[0].code").value("USUARIO_CPF_INVALIDO"))
                .andExpect(content().string(not(containsString("52998224724"))));
    }

    @Test
    void emailSemDominioCompletoDeveRetornar400NoCampoEmail() throws Exception {
        ResultActions resultado = cadastrar(corpo("Maria", CPF, "a@b", "senha-forte-123"));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("USUARIO_EMAIL_INVALIDO"));
    }

    @Test
    void cpfJaCadastradoDeveRetornar409NoCampoCpf() throws Exception {
        cadastrarValido().andExpect(status().isCreated());

        ResultActions resultado = cadastrar(corpo("Outra", CPF, "outra@exemplo.com", "senha-forte-123"));

        assertProblema(resultado, 409, "CADASTRO_JA_EXISTENTE");
        resultado.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("cpf")))
                .andExpect(content().string(not(containsString(CPF))));
    }

    @Test
    void cpfEEmailJaCadastradosDevemRetornar409ComOsDoisCampos() throws Exception {
        cadastrarValido().andExpect(status().isCreated());

        ResultActions resultado = cadastrar(corpo("Outra", CPF, EMAIL, "senha-forte-123"));

        assertProblema(resultado, 409, "CADASTRO_JA_EXISTENTE");
        resultado.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("cpf", "email")))
                .andExpect(content().string(not(containsString(EMAIL))));
    }

    @Test
    void correlationIdDoClienteDeveSerDevolvidoNoCabecalhoENoCorpo() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("X-Correlation-Id", "app-teste-42")
                        .content(corpo("Maria", "52998224724", EMAIL, "senha-forte-123")))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Correlation-Id", "app-teste-42"))
                .andExpect(jsonPath("$.correlationId").value("app-teste-42"));
    }

    @Test
    void rotaInexistenteDeveRetornar404() throws Exception {
        assertProblema(mockMvc.perform(get("/api/v1/inexistente")), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void catalogoDeOcorrenciasLegadoDeveContinuarFuncionando() throws Exception {
        mockMvc.perform(get("/api/v1/ocorrencias").param("orgao", "SAMU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)));
    }

    @Test
    void orgaoInvalidoNoCatalogoDeveRetornar400SemRepetirOValor() throws Exception {
        ResultActions resultado = mockMvc.perform(get("/api/v1/ocorrencias").param("orgao", "XYZ-INVALIDO"));

        assertProblema(resultado, 400, "VALIDACAO_INVALIDA");
        resultado.andExpect(jsonPath("$.errors[0].field").value("orgao"))
                .andExpect(content().string(not(containsString("XYZ-INVALIDO"))));
    }
}
