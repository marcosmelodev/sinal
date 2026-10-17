package br.edu.udf.cs.sinal.plataforma.web;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;

// Erros em application/problem+json (Etapa 4 §16): code estável, detail seguro (frase fixa, sem valores
// enviados nem detalhes internos), errors[] por campo e correlationId. Nunca há stack trace na resposta.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String VALIDACAO_INVALIDA = "VALIDACAO_INVALIDA";
    static final String RECURSO_NAO_ENCONTRADO = "RECURSO_NAO_ENCONTRADO";
    static final String ERRO_INTERNO = "ERRO_INTERNO";

    private static final String DETALHE_VALIDACAO = "Os dados enviados são inválidos.";
    private static final String DETALHE_NAO_ENCONTRADO = "O recurso solicitado não foi encontrado.";
    private static final String DETALHE_ERRO_INTERNO = "Ocorreu um erro inesperado. Tente novamente mais tarde.";
    private static final String DETALHE_REQUISICAO = "A requisição não pôde ser processada.";

    // Campo de entrada associado a cada código de validação do domínio; os módulos acrescentam os seus.
    private static final Map<String, String> CAMPO_POR_CODIGO_DE_DOMINIO = Map.of(
            "USUARIO_NOME_OBRIGATORIO", "nome",
            "USUARIO_CPF_INVALIDO", "cpf",
            "USUARIO_EMAIL_INVALIDO", "email",
            "USUARIO_SENHA_OBRIGATORIA", "senha",
            "CONTATO_NOME_OBRIGATORIO", "nome",
            "CONTATO_TELEFONE_OBRIGATORIO", "telefone",
            "CONTATO_TELEFONE_INVALIDO", "telefone");

    // Demais DominioException são validação de entrada (400).
    private static final Set<String> REGRAS_DE_NEGOCIO = Set.of("LIMITE_CONTATOS_EXCEDIDO");
    private static final Set<String> NAO_ENCONTRADOS = Set.of("CONTATO_NAO_ENCONTRADO");

    private static final String SENHA_INVALIDA = "SENHA_INVALIDA";
    private static final String CADASTRO_JA_EXISTENTE = "CADASTRO_JA_EXISTENTE";
    private static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DominioException.class)
    public ResponseEntity<ProblemDetail> tratarDominio(DominioException ex) {
        if (REGRAS_DE_NEGOCIO.contains(ex.getCodigo())) {
            return resposta(problema(HttpStatus.UNPROCESSABLE_CONTENT, ex.getCodigo(), ex.getMensagem(), List.of()));
        }
        if (NAO_ENCONTRADOS.contains(ex.getCodigo())) {
            return resposta(problema(HttpStatus.NOT_FOUND, RECURSO_NAO_ENCONTRADO, DETALHE_NAO_ENCONTRADO, List.of()));
        }
        ErroDeCampo erro = new ErroDeCampo(CAMPO_POR_CODIGO_DE_DOMINIO.get(ex.getCodigo()), ex.getCodigo(),
                ex.getMensagem());
        return resposta(problema(HttpStatus.BAD_REQUEST, VALIDACAO_INVALIDA, DETALHE_VALIDACAO, List.of(erro)));
    }

    @ExceptionHandler(AplicacaoException.class)
    public ResponseEntity<ProblemDetail> tratarAplicacao(AplicacaoException ex) {
        List<ErroDeCampo> erros = ex.getCampos().stream().sorted()
                .map(campo -> new ErroDeCampo(campo, ex.getCodigo(), ex.getMensagem()))
                .toList();
        if (SENHA_INVALIDA.equals(ex.getCodigo())) {
            return resposta(problema(HttpStatus.BAD_REQUEST, VALIDACAO_INVALIDA, DETALHE_VALIDACAO, erros));
        }
        if (NAO_AUTENTICADO.equals(ex.getCodigo())) {
            return resposta(problema(HttpStatus.UNAUTHORIZED, ex.getCodigo(), ex.getMensagem(), List.of()));
        }
        if (CADASTRO_JA_EXISTENTE.equals(ex.getCodigo())) {
            return resposta(problema(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMensagem(), erros));
        }
        return resposta(problema(HttpStatus.UNPROCESSABLE_CONTENT, ex.getCodigo(), ex.getMensagem(), erros));
    }

    // Inclui IllegalArgumentException: no modelo, indica erro de programação, não entrada inválida.
    // Registra apenas o tipo: mensagens de exceções podem conter dados pessoais (Etapa 4 §23).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarInesperado(Exception ex) {
        LOG.error("Erro inesperado {} [correlationId={}]", ex.getClass().getName(), MDC.get(CorrelationIdFilter.CHAVE_MDC));
        LOG.debug("Detalhes do erro inesperado", ex);
        return resposta(problema(HttpStatus.INTERNAL_SERVER_ERROR, ERRO_INTERNO, DETALHE_ERRO_INTERNO, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErroDeCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::erroDeValidacao)
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        return new ResponseEntity<>(problema(HttpStatus.BAD_REQUEST, VALIDACAO_INVALIDA, DETALHE_VALIDACAO, erros),
                headers, HttpStatus.BAD_REQUEST);
    }

    // Demais exceções do Spring MVC: mantém o status do framework e padroniza code, detail e correlationId.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String code;
        String detalhe;
        if (status == HttpStatus.NOT_FOUND) {
            code = RECURSO_NAO_ENCONTRADO;
            detalhe = DETALHE_NAO_ENCONTRADO;
        } else if (status.is5xxServerError()) {
            code = ERRO_INTERNO;
            detalhe = DETALHE_ERRO_INTERNO;
        } else {
            code = VALIDACAO_INVALIDA;
            detalhe = status == HttpStatus.BAD_REQUEST ? DETALHE_VALIDACAO : DETALHE_REQUISICAO;
        }
        return new ResponseEntity<>(problema(status, code, detalhe, errosDoFramework(ex)), headers, status);
    }

    private static List<ErroDeCampo> errosDoFramework(Exception ex) {
        if (ex instanceof TypeMismatchException tipo && tipo.getPropertyName() != null) {
            return List.of(new ErroDeCampo(tipo.getPropertyName(), "VALOR_INVALIDO", "Valor inválido."));
        }
        if (ex instanceof MissingServletRequestParameterException ausente) {
            return List.of(new ErroDeCampo(ausente.getParameterName(), "CAMPO_OBRIGATORIO", "Campo obrigatório."));
        }
        return List.of();
    }

    // Mensagens fixas: não dependem do idioma do servidor nem repetem o valor enviado.
    private static ErroDeCampo erroDeValidacao(FieldError erro) {
        String restricao = erro.getCode() == null ? "" : erro.getCode();
        return switch (restricao) {
            case "NotBlank", "NotNull", "NotEmpty" ->
                    new ErroDeCampo(erro.getField(), "CAMPO_OBRIGATORIO", "Campo obrigatório.");
            case "Size" -> new ErroDeCampo(erro.getField(), "TAMANHO_INVALIDO", "Tamanho inválido.");
            default -> new ErroDeCampo(erro.getField(), "VALOR_INVALIDO", "Valor inválido.");
        };
    }

    private static ProblemDetail problema(HttpStatus status, String code, String detalhe, List<ErroDeCampo> erros) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setProperty("code", code);
        problema.setProperty("correlationId", MDC.get(CorrelationIdFilter.CHAVE_MDC));
        problema.setProperty("errors", erros);
        return problema;
    }

    private static ResponseEntity<ProblemDetail> resposta(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }
}
