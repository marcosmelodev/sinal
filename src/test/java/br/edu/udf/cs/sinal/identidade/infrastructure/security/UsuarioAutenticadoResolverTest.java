package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsuarioAutenticadoResolverTest {

    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");

    private UsuarioRepository repository;
    private UsuarioAutenticadoResolver resolver;

    @SuppressWarnings("unused")
    void endpoint(@UsuarioAutenticado Long usuarioId, Long semAnotacao) {
    }

    private static MethodParameter parametro(int indice) throws NoSuchMethodException {
        return new MethodParameter(UsuarioAutenticadoResolverTest.class.getDeclaredMethod("endpoint", Long.class,
                Long.class), indice);
    }

    private static void autenticarComSub(String sub) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(sub).issuedAt(AGORA)
                .expiresAt(AGORA.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Long resolver() throws Exception {
        return resolver.resolveArgument(parametro(0), null, null, null);
    }

    private void assertCodigo(String codigo) {
        assertThatThrownBy(this::resolver).isInstanceOfSatisfying(AplicacaoException.class,
                e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    @BeforeEach
    void configurar() {
        repository = mock(UsuarioRepository.class);
        resolver = new UsuarioAutenticadoResolver(repository);
        when(repository.buscarPorId(any())).thenReturn(Optional.empty());
        when(repository.buscarPorId(7L)).thenReturn(Optional.of(
                Usuario.reconstituir(7L, "Maria", "52998224725", "maria@exemplo.com.br", "hash", AGORA, true)));
        when(repository.buscarPorId(8L)).thenReturn(Optional.of(
                Usuario.reconstituir(8L, "Ana", "11144477735", "ana@exemplo.com.br", "hash", AGORA, false)));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveSuportarSomenteParametrosLongAnotados() throws Exception {
        assertThat(resolver.supportsParameter(parametro(0))).isTrue();
        assertThat(resolver.supportsParameter(parametro(1))).isFalse();
    }

    @Test
    void deveDevolverOIdDoSubDeUsuarioAtivo() throws Exception {
        autenticarComSub("7");

        assertThat(resolver()).isEqualTo(7L);
    }

    @Test
    void semAutenticacaoOuAutenticacaoQueNaoEJwtDeveSerNaoAutenticado() {
        assertCodigo("NAO_AUTENTICADO");

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", null));
        assertCodigo("NAO_AUTENTICADO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-7", "7.0", "99999999999999999999"})
    void subInvalidoDeveSerNaoAutenticado(String sub) {
        autenticarComSub(sub);

        assertCodigo("NAO_AUTENTICADO");
    }

    @Test
    void usuarioInexistenteDeveSerNaoAutenticado() {
        autenticarComSub("999");

        assertCodigo("NAO_AUTENTICADO");
    }

    @Test
    void usuarioInativoDeveSerContaInativa() {
        autenticarComSub("8");

        assertCodigo("CONTA_INATIVA");
    }
}
