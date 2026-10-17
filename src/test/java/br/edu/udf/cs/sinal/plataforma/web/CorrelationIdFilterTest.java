package br.edu.udf.cs.sinal.plataforma.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    private String executar(String cabecalho, AtomicReference<String> mdcDuranteACadeia) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/qualquer");
        if (cabecalho != null) {
            request.addHeader(CorrelationIdFilter.CABECALHO, cabecalho);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadeia = (req, res) -> mdcDuranteACadeia.set(MDC.get(CorrelationIdFilter.CHAVE_MDC));

        filtro.doFilter(request, response, cadeia);

        return response.getHeader(CorrelationIdFilter.CABECALHO);
    }

    @Test
    void deveGerarIdentificadorQuandoAusente() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();

        String devolvido = executar(null, mdc);

        assertThat(devolvido).matches("[0-9a-f-]{36}");
        assertThat(mdc.get()).isEqualTo(devolvido);
    }

    @Test
    void deveAceitarIdentificadorBemFormadoDoCliente() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();

        assertThat(executar("app-123-ABC", mdc)).isEqualTo("app-123-ABC");
        assertThat(mdc.get()).isEqualTo("app-123-ABC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "com espaço", "injeção\nde-linha", "a<script>", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void deveSubstituirIdentificadorMalFormado(String recebido) throws Exception {
        String devolvido = executar(recebido, new AtomicReference<>());

        assertThat(devolvido).isNotEqualTo(recebido).matches("[0-9a-f-]{36}");
    }

    @Test
    void deveLimparOMdcAoFinal() throws Exception {
        executar("app-123", new AtomicReference<>());

        assertThat(MDC.get(CorrelationIdFilter.CHAVE_MDC)).isNull();
    }
}
