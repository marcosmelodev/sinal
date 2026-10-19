package br.edu.udf.cs.sinal.libras.infrastructure.openai;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Unitário, sem contexto Spring nem rede: o MockRestServiceServer substitui a API da OpenAI.
class OpenAiLibrasServiceTest {

    private static final String URL = "https://openai.teste/v1/chat/completions";
    private static final String MODELO = "gpt-4o-mini";
    private static final String CHAVE = "sk-chave-de-teste";

    private MockRestServiceServer servidor;
    private RestClient restClient;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private OpenAiLibrasService servico(String chave) {
        return new OpenAiLibrasService(restClient, URL, MODELO, chave);
    }

    private static String respostaComTexto(String texto) {
        return """
                {"id": "chatcmpl-1", "object": "chat.completion", "model": "gpt-4o-mini-2024-07-18",
                 "choices": [{"index": 0, "message": {"role": "assistant", "content": "%s", "refusal": null},
                              "finish_reason": "stop"}],
                 "usage": {"prompt_tokens": 120, "completion_tokens": 12, "total_tokens": 132}}""".formatted(texto);
    }

    private static void assertIndisponivel(Runnable chamada) {
        assertThatThrownBy(chamada::run)
                .isInstanceOf(AplicacaoException.class)
                .extracting(ex -> ((AplicacaoException) ex).getCodigo())
                .isEqualTo(OpenAiLibrasService.CHAT_INDISPONIVEL);
    }

    @Test
    void deveEnviarPayloadDeChatCompletionsEDevolverOTextoGerado() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CHAVE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value(MODELO))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content",
                        containsString("Você é um atendente virtual humanizado de plantão")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Meu pai desmaiou, o que eu faço?"))
                .andExpect(jsonPath("$.temperature").value(0.4))
                .andRespond(withSuccess(respostaComTexto("  Fique calmo. Veja se ele respira.  "),
                        MediaType.APPLICATION_JSON));

        String resposta = servico(CHAVE).responder("Meu pai desmaiou, o que eu faço?");

        assertThat(resposta).isEqualTo("Fique calmo. Veja se ele respira.");
        servidor.verify();
    }

    @Test
    void chaveNaoDeveIrNaUrl() {
        servidor.expect(requestTo(URL))
                .andExpect(request -> assertThat(request.getURI().toString()).doesNotContain(CHAVE))
                .andRespond(withSuccess(respostaComTexto("Estou aqui com você."), MediaType.APPLICATION_JSON));

        servico(CHAVE).responder("Estou com medo");

        servidor.verify();
    }

    @Test
    void instrucaoDoSistemaDeveConterOTextoPedidoEOsLimitesDeSeguranca() {
        assertThat(OpenAiLibrasService.INSTRUCAO_DO_SISTEMA)
                .contains("Você é um atendente virtual humanizado de plantão em uma central de emergência voltada "
                        + "para a comunidade surda (projeto SINAL). Suas respostas devem ser curtas, diretas, "
                        + "acolhedoras, em português claro, focadas em orientar primeiros socorros ou acalmar o "
                        + "usuário em situações de crise.")
                .contains("Nunca diga que uma ajuda foi enviada")
                .contains("SAMU 192");
    }

    @Test
    void semChaveConfiguradaNaoDeveChamarAApi() {
        assertIndisponivel(() -> servico("  ").responder("Socorro"));
        assertIndisponivel(() -> servico(null).responder("Socorro"));
        servidor.verify();
    }

    @Test
    void erroDoServidorDaOpenAiDeveVirarChatIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());

        assertIndisponivel(() -> servico(CHAVE).responder("Socorro"));
        servidor.verify();
    }

    @Test
    void chaveInvalidaOuLimiteDeCotaDeveVirarChatIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        OpenAiLibrasService servico = servico(CHAVE);
        assertIndisponivel(() -> servico.responder("Socorro"));
        assertIndisponivel(() -> servico.responder("Socorro"));
        servidor.verify();
    }

    @Test
    void tempoEsgotadoDeveVirarChatIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertIndisponivel(() -> servico(CHAVE).responder("Socorro"));
        servidor.verify();
    }

    @Test
    void respostaSemEscolhasDeveVirarChatIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));

        assertIndisponivel(() -> servico(CHAVE).responder("Socorro"));
        servidor.verify();
    }

    @Test
    void respostaSemTextoOuRecusadaDeveVirarChatIndisponivel() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(respostaComTexto("   "), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices": [{"message": {"role": "assistant", "content": null,
                 "refusal": "Não posso ajudar com isso."}, "finish_reason": "stop"}]}""",
                MediaType.APPLICATION_JSON));

        OpenAiLibrasService servico = servico(CHAVE);
        assertIndisponivel(() -> servico.responder("Socorro"));
        assertIndisponivel(() -> servico.responder("Socorro"));
        servidor.verify();
    }
}
