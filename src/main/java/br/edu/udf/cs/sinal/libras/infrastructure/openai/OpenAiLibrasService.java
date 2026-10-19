package br.edu.udf.cs.sinal.libras.infrastructure.openai;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

// Chat da central em LIBRAS: envia a mensagem do usuário à OpenAI (chat completions) e devolve o texto gerado.
// Qualquer falha (chave ausente, erro HTTP, tempo esgotado, resposta vazia ou recusada) vira 503 CHAT_INDISPONIVEL.
// A mensagem, a resposta e a chave nunca aparecem em logs (Etapa 4 §23).
public class OpenAiLibrasService {

    public static final String CHAT_INDISPONIVEL = "CHAT_INDISPONIVEL";

    // Persona de atendente da central, sem avisos robóticos. Limites mantidos de propósito: o SINAL não despacha
    // equipes no MVP (doc 05: nenhum texto pode sugerir atendimento real), então a IA nunca afirma que o socorro
    // foi enviado; e não mente sobre ser um assistente virtual se a pessoa perguntar com sinceridade.
    static final String INSTRUCAO_DO_SISTEMA = """
            Você é um atendente humanizado de plantão da central de emergência do projeto SINAL, voltada para a \
            comunidade surda. Fale como um operador acolhedor: frases curtas, diretas e calmas, em português claro \
            e simples (para muitas pessoas surdas, o português é a segunda língua).

            Como conduzir o atendimento:
            - Primeiro acolha e acalme. Depois faça perguntas diretas de triagem, uma ou duas por vez, como \
            "O que está acontecendo?", "Onde você está?", "Alguém está ferido?" e "A pessoa está respirando?".
            - Oriente primeiros socorros simples e seguros, passo a passo.
            - Em risco de vida, diga com clareza qual serviço acionar: SAMU 192, Bombeiros 193 ou Polícia 190, \
            se preciso com a ajuda de alguém por perto.
            - Mantenha o atendimento fluido e empático. Não use avisos robóticos como "sou apenas uma \
            inteligência artificial" e não comente sobre como você funciona.

            Limites que você nunca quebra:
            - Nunca afirme que uma equipe, viatura ou ambulância foi enviada, que um órgão recebeu o pedido ou que \
            o socorro está a caminho: esta central não despacha equipes. Diga o que a pessoa deve fazer agora.
            - Se a pessoa perguntar com sinceridade se está falando com uma pessoa ou com um robô, responda com \
            honestidade, em uma frase, que é o assistente virtual da central, e continue ajudando.
            - Não faça diagnósticos nem indique medicamentos ou doses.
            - Responda apenas sobre emergências, primeiros socorros, segurança e acolhimento.""";

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiLibrasService.class);

    private final RestClient restClient;
    private final String url;
    private final String modelo;
    private final String chave;

    public OpenAiLibrasService(RestClient restClient, String url, String modelo, String chave) {
        this.restClient = Objects.requireNonNull(restClient);
        this.url = Objects.requireNonNull(url);
        this.modelo = Objects.requireNonNull(modelo);
        this.chave = chave == null ? "" : chave.strip();
    }

    public String responder(String mensagem) {
        if (chave.isEmpty()) {
            LOG.warn("Chat em LIBRAS indisponível: openai.api.key não configurada");
            throw indisponivel();
        }
        RespostaOpenAi resposta;
        try {
            resposta = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + chave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(RequisicaoOpenAi.de(modelo, mensagem))
                    .retrieve()
                    .body(RespostaOpenAi.class);
        } catch (RestClientResponseException ex) {
            LOG.warn("OpenAI respondeu HTTP {}", ex.getStatusCode().value());
            throw indisponivel();
        } catch (RestClientException ex) {
            LOG.warn("Falha ao chamar a OpenAI: {}", ex.getClass().getSimpleName());
            throw indisponivel();
        }
        String texto = resposta == null ? "" : resposta.texto();
        if (texto.isBlank()) {
            LOG.warn("OpenAI não devolveu texto (resposta vazia ou recusada)");
            throw indisponivel();
        }
        return texto.strip();
    }

    private static AplicacaoException indisponivel() {
        return new AplicacaoException(CHAT_INDISPONIVEL,
                "O chat está indisponível no momento. Em risco de vida, acione os serviços de emergência.", Set.of());
    }

    // Payload do POST /v1/chat/completions.
    record RequisicaoOpenAi(String model, List<Mensagem> messages, double temperature) {

        static RequisicaoOpenAi de(String modelo, String mensagem) {
            return new RequisicaoOpenAi(modelo,
                    List.of(new Mensagem("system", INSTRUCAO_DO_SISTEMA), new Mensagem("user", mensagem)),
                    0.4);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Mensagem(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaOpenAi(List<Escolha> choices) {

        // Texto de choices[0].message.content; sem escolha ou sem conteúdo (ex.: recusa), devolve "".
        String texto() {
            Escolha primeira = choices == null || choices.isEmpty() ? null : choices.get(0);
            if (primeira == null || primeira.message() == null || primeira.message().content() == null) {
                return "";
            }
            return primeira.message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Escolha(Mensagem message) {
    }
}
