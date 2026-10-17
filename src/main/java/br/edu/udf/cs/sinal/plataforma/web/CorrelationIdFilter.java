package br.edu.udf.cs.sinal.plataforma.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Identificador de correlação por requisição (Etapa 4 §16): aceito do cliente quando bem formado,
// gerado caso contrário; disponível no MDC dos logs, no cabeçalho da resposta e no corpo dos erros.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String CHAVE_MDC = "correlationId";

    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolver(request.getHeader(CABECALHO));
        MDC.put(CHAVE_MDC, correlationId);
        response.setHeader(CABECALHO, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }

    private static String resolver(String recebido) {
        if (recebido != null && FORMATO_VALIDO.matcher(recebido).matches()) {
            return recebido;
        }
        return UUID.randomUUID().toString();
    }
}
