package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.RelogioAjustavel;
import br.edu.udf.cs.sinal.identidade.application.TokenEmitido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenJwtServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");
    private static final SecretKey CHAVE = JwtConfig.chave("segredo-de-testes-automatizados-com-mais-de-32-bytes");

    private RelogioAjustavel relogio;
    private JwtDecoder decoder;
    private TokenJwtService service;

    @BeforeEach
    void configurar() {
        relogio = new RelogioAjustavel(AGORA);
        decoder = JwtConfig.decodificador(CHAVE, relogio);
        service = novoServico(CHAVE, "PT15M");
    }

    private TokenJwtService novoServico(SecretKey chave, String validade) {
        return new TokenJwtService(NimbusJwtEncoder.withSecretKey(chave).build(), decoder, relogio, validade);
    }

    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deveEmitirTokenComClaimsMinimosEValidadeDe15Minutos() {
        TokenEmitido emitido = service.emitir(42L);

        Jwt jwt = decoder.decode(emitido.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("sinal");
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "iss", "iat", "exp");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void deveValidarEExtrairOIdDoUsuario() {
        assertThat(service.validarEExtrairUsuarioId(service.emitir(7L).accessToken())).isEqualTo(7L);
    }

    @Test
    void tokenExpiradoDeveSerRecusado() {
        String token = service.emitir(7L).accessToken();

        relogio.avancar(Duration.ofMinutes(15));
        assertThat(service.validarEExtrairUsuarioId(token)).isEqualTo(7L);
        relogio.avancar(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service.validarEExtrairUsuarioId(token)).isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenAssinadoComOutraChaveDeveSerRecusado() {
        String token = novoServico(JwtConfig.chave("outra-chave-qualquer-com-mais-de-32-bytes!!"), "PT15M")
                .emitir(7L).accessToken();

        assertThatThrownBy(() -> service.validarEExtrairUsuarioId(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenAdulteradoDeveSerRecusado() {
        String[] partes = service.emitir(7L).accessToken().split("\\.");
        String adulterado = partes[0] + "." + base64("{\"sub\":\"1\",\"iss\":\"sinal\",\"exp\":4102444800}")
                + "." + partes[2];

        assertThatThrownBy(() -> service.validarEExtrairUsuarioId(adulterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSemAssinaturaDeveSerRecusado() {
        String semAssinatura = base64("{\"alg\":\"none\"}") + "."
                + base64("{\"sub\":\"1\",\"iss\":\"sinal\",\"exp\":4102444800}") + ".";

        assertThatThrownBy(() -> service.validarEExtrairUsuarioId(semAssinatura)).isInstanceOf(JwtException.class);
    }

    @Test
    void validadeInvalidaDeveImpedirAInicializacao() {
        assertThatThrownBy(() -> novoServico(CHAVE, "PT0S")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> novoServico(CHAVE, "PT-1M")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toStringDoTokenEmitidoNaoDeveExporOToken() {
        TokenEmitido emitido = service.emitir(7L);

        assertThat(emitido.toString()).doesNotContain(emitido.accessToken());
    }
}
