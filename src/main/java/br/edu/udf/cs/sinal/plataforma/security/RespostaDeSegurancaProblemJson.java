package br.edu.udf.cs.sinal.plataforma.security;

import br.edu.udf.cs.sinal.plataforma.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Erros da cadeia de segurança (antes do Spring MVC) no mesmo formato problem+json do GlobalExceptionHandler
// (Etapa 4 §16): code, detail seguro, errors[] e correlationId. Nunca expõe o motivo técnico da recusa do token.
@Component
public class RespostaDeSegurancaProblemJson implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";
    static final String TOKEN_EXPIRADO = "TOKEN_EXPIRADO";
    static final String ACESSO_NEGADO = "ACESSO_NEGADO";

    private final JsonMapper jsonMapper;

    public RespostaDeSegurancaProblemJson(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException excecao)
            throws IOException {
        boolean expirado = tokenExpirado(excecao);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(request, response, HttpStatus.UNAUTHORIZED, expirado ? TOKEN_EXPIRADO : NAO_AUTENTICADO,
                expirado ? "Sessão expirada. Faça login novamente." : "Autenticação necessária.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException excecao)
            throws IOException {
        escrever(request, response, HttpStatus.FORBIDDEN, ACESSO_NEGADO, "Acesso negado.");
    }

    // O validador de tempo do Spring Security informa "Jwt expired at ..." na cadeia de causas.
    private static boolean tokenExpirado(Throwable excecao) {
        for (Throwable atual = excecao; atual != null; atual = atual.getCause()) {
            if (atual.getMessage() != null && atual.getMessage().toLowerCase(Locale.ROOT).contains("jwt expired")) {
                return true;
            }
        }
        return false;
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                          String detalhe) throws IOException {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", status.getReasonPhrase());
        corpo.put("status", status.value());
        corpo.put("detail", detalhe);
        corpo.put("instance", request.getRequestURI());
        corpo.put("code", code);
        corpo.put("correlationId", MDC.get(CorrelationIdFilter.CHAVE_MDC));
        corpo.put("errors", List.of());
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(corpo));
    }
}
