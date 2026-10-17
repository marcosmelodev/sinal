package br.edu.udf.cs.sinal.plataforma.web;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "teste-123";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/x"));

    @BeforeEach
    void definirCorrelationId() {
        MDC.put(CorrelationIdFilter.CHAVE_MDC, CORRELATION_ID);
    }

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @SuppressWarnings("unchecked")
    private static List<ErroDeCampo> erros(ProblemDetail problema) {
        return (List<ErroDeCampo>) problema.getProperties().get("errors");
    }

    private static void assertProblema(ResponseEntity<?> resposta, HttpStatus status, String code) {
        assertThat(resposta.getStatusCode()).isEqualTo(status);
        ProblemDetail problema = (ProblemDetail) resposta.getBody();
        assertThat(problema).isNotNull();
        assertThat(problema.getStatus()).isEqualTo(status.value());
        assertThat(problema.getProperties()).containsEntry("code", code).containsEntry("correlationId", CORRELATION_ID);
    }

    @Test
    void validacaoDeDominioComCampoConhecidoDeveSer400ComOCampo() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarDominio(
                new DominioException("USUARIO_CPF_INVALIDO", "O CPF informado é inválido."));

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        assertThat(erros(resposta.getBody()))
                .containsExactly(new ErroDeCampo("cpf", "USUARIO_CPF_INVALIDO", "O CPF informado é inválido."));
    }

    @Test
    void validacaoDeDominioSemCampoMapeadoDeveSer400SemCampo() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarDominio(
                new DominioException("LOCALIZACAO_LATITUDE_INVALIDA", "A latitude deve estar entre -90 e 90."));

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        assertThat(erros(resposta.getBody())).extracting(ErroDeCampo::field).containsExactly((String) null);
    }

    @Test
    void regraDeNegocioDeveSer422ComOProprioCodigo() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarDominio(
                new DominioException("LIMITE_CONTATOS_EXCEDIDO", "O limite de 5 contatos de confiança foi atingido."));

        assertProblema(resposta, HttpStatus.UNPROCESSABLE_CONTENT, "LIMITE_CONTATOS_EXCEDIDO");
        assertThat(resposta.getBody().getDetail()).isEqualTo("O limite de 5 contatos de confiança foi atingido.");
    }

    @Test
    void contatoNaoEncontradoDeveSer404RecursoNaoEncontrado() {
        assertProblema(handler.tratarDominio(new DominioException("CONTATO_NAO_ENCONTRADO", "Contato não encontrado.")),
                HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void cadastroJaExistenteDeveSer409ComUmErroPorCampo() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarAplicacao(new AplicacaoException(
                "CADASTRO_JA_EXISTENTE", "Já existe cadastro com os dados informados.", Set.of("email", "cpf")));

        assertProblema(resposta, HttpStatus.CONFLICT, "CADASTRO_JA_EXISTENTE");
        assertThat(erros(resposta.getBody())).extracting(ErroDeCampo::field, ErroDeCampo::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("cpf", "CADASTRO_JA_EXISTENTE"),
                        org.assertj.core.groups.Tuple.tuple("email", "CADASTRO_JA_EXISTENTE"));
    }

    @Test
    void senhaInvalidaDeveSer400ComOCampoSenha() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarAplicacao(
                new AplicacaoException("SENHA_INVALIDA", "A senha deve ter no mínimo 8 caracteres.", Set.of("senha")));

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        assertThat(erros(resposta.getBody())).extracting(ErroDeCampo::field, ErroDeCampo::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("senha", "SENHA_INVALIDA"));
    }

    @Test
    void aplicacaoDesconhecidaDeveSer422ComOProprioCodigo() {
        assertProblema(handler.tratarAplicacao(new AplicacaoException("OUTRA_REGRA", "Regra.", Set.of())),
                HttpStatus.UNPROCESSABLE_CONTENT, "OUTRA_REGRA");
    }

    @Test
    void erroInesperadoDeveSer500SemExporAMensagemInterna() {
        ResponseEntity<ProblemDetail> resposta = handler.tratarInesperado(
                new IllegalStateException("Duplicate entry '52998224725' for key 'usuario.uk_usuario_cpf'"));

        assertProblema(resposta, HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO");
        assertThat(resposta.getBody().getDetail()).doesNotContain("52998224725").doesNotContain("uk_usuario");
        assertThat(erros(resposta.getBody())).isEmpty();
    }

    @Test
    void illegalArgumentExceptionDeveSer500() {
        assertProblema(handler.tratarInesperado(new IllegalArgumentException("Erro de programação.")),
                HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO");
    }

    @Test
    void rotaInexistenteDeveSer404RecursoNaoEncontrado() throws Exception {
        ResponseEntity<Object> resposta = handler.handleException(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/inexistente", "api/v1/inexistente"), request);

        assertProblema(resposta, HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void jsonMalformadoDeveSer400() throws Exception {
        ResponseEntity<Object> resposta = handler.handleException(new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage(new byte[0])), request);

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        assertThat(((ProblemDetail) resposta.getBody()).getDetail()).doesNotContain("JSON parse error");
    }

    @Test
    void parametroAusenteDeveSer400ComOCampo() throws Exception {
        ResponseEntity<Object> resposta = handler.handleException(
                new MissingServletRequestParameterException("orgao", "OrgaoSeguranca"), request);

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        assertThat(erros((ProblemDetail) resposta.getBody())).extracting(ErroDeCampo::field, ErroDeCampo::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("orgao", "CAMPO_OBRIGATORIO"));
    }

    @Test
    void tipoInvalidoDeveSer400SemRepetirOValorEnviado() throws Exception {
        TypeMismatchException ex = new TypeMismatchException("XYZ-valor-enviado", Enum.class);
        ex.initPropertyName("orgao");

        ResponseEntity<Object> resposta = handler.handleException(ex, request);

        assertProblema(resposta, HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA");
        ProblemDetail problema = (ProblemDetail) resposta.getBody();
        assertThat(problema.getDetail()).doesNotContain("XYZ-valor-enviado");
        assertThat(erros(problema)).extracting(ErroDeCampo::field).containsExactly("orgao");
    }

    @Test
    void metodoNaoSuportadoDeveManterOStatusDoFramework() throws Exception {
        ResponseEntity<Object> resposta = handler.handleException(
                new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertProblema(resposta, HttpStatus.METHOD_NOT_ALLOWED, "VALIDACAO_INVALIDA");
    }
}
