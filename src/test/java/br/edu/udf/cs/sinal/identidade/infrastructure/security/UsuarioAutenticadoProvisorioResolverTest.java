package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UsuarioAutenticadoProvisorioResolverTest {

    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");

    private UsuarioRepository repository;

    @SuppressWarnings("unused")
    void endpoint(@UsuarioAutenticado Long usuarioId, Long semAnotacao, @UsuarioAutenticado String tipoErrado) {
    }

    private static MethodParameter parametro(int indice) throws NoSuchMethodException {
        Method metodo = UsuarioAutenticadoProvisorioResolverTest.class
                .getDeclaredMethod("endpoint", Long.class, Long.class, String.class);
        return new MethodParameter(metodo, indice);
    }

    private static ServletWebRequest requisicao(String cabecalho) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (cabecalho != null) {
            request.addHeader(UsuarioAutenticadoProvisorioResolver.CABECALHO, cabecalho);
        }
        return new ServletWebRequest(request);
    }

    private static Usuario usuario(Long id, boolean ativo) {
        return Usuario.reconstituir(id, "Maria", "52998224725", "maria@exemplo.com.br", "hash", AGORA, ativo);
    }

    private static void assertNaoAutenticado(Runnable resolucao) {
        assertThatThrownBy(resolucao::run)
                .isInstanceOfSatisfying(AplicacaoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("NAO_AUTENTICADO"));
    }

    @BeforeEach
    void configurar() {
        repository = mock(UsuarioRepository.class);
        when(repository.buscarPorId(any())).thenReturn(Optional.empty());
        when(repository.buscarPorId(7L)).thenReturn(Optional.of(usuario(7L, true)));
        when(repository.buscarPorId(8L)).thenReturn(Optional.of(usuario(8L, false)));
    }

    @Test
    void deveSuportarSomenteParametrosLongAnotados() throws Exception {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, true);

        assertThat(resolver.supportsParameter(parametro(0))).isTrue();
        assertThat(resolver.supportsParameter(parametro(1))).isFalse();
        assertThat(resolver.supportsParameter(parametro(2))).isFalse();
    }

    @Test
    void deveDevolverOIdDeUsuarioExistenteEAtivo() throws Exception {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, true);

        assertThat(resolver.resolveArgument(parametro(0), null, requisicao("7"), null)).isEqualTo(7L);
    }

    @Test
    void desligadoDeveRecusarMesmoComUsuarioValido() {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, false);

        assertNaoAutenticado(() -> resolver.resolveArgument(parametro0(), null, requisicao("7"), null));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-7", "7.0", " 7", "99999999999999999999"})
    void cabecalhoAusenteOuInvalidoDeveSerRecusado(String cabecalho) {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, true);

        assertNaoAutenticado(() -> resolver.resolveArgument(parametro0(), null, requisicao(cabecalho), null));
    }

    @Test
    void usuarioInexistenteDeveSerRecusado() {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, true);

        assertNaoAutenticado(() -> resolver.resolveArgument(parametro0(), null, requisicao("999"), null));
    }

    @Test
    void usuarioInativoDeveSerRecusado() {
        UsuarioAutenticadoProvisorioResolver resolver = new UsuarioAutenticadoProvisorioResolver(repository, true);

        assertNaoAutenticado(() -> resolver.resolveArgument(parametro0(), null, requisicao("8"), null));
    }

    private static MethodParameter parametro0() {
        try {
            return parametro(0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
