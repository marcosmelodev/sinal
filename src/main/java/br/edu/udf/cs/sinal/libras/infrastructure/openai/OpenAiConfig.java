package br.edu.udf.cs.sinal.libras.infrastructure.openai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Cliente HTTP da OpenAI com tempos limite: o chat nunca deixa uma requisição presa esperando a API externa.
@Configuration
public class OpenAiConfig {

    @Bean
    OpenAiLibrasService openAiLibrasService(@Value("${openai.api.url}") String url,
                                            @Value("${openai.api.model:gpt-4o-mini}") String modelo,
                                            @Value("${openai.api.key:}") String chave,
                                            @Value("${openai.api.timeout-conexao:PT10S}") Duration timeoutConexao,
                                            @Value("${openai.api.timeout-resposta:PT30S}") Duration timeoutResposta) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutConexao);
        fabrica.setReadTimeout(timeoutResposta);
        return new OpenAiLibrasService(RestClient.builder().requestFactory(fabrica).build(), url, modelo, chave);
    }
}
