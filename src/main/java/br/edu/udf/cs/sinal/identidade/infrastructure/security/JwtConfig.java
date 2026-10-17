package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

// JWT HS256 (Nimbus JOSE): a mesma aplicação emite e valida. O segredo vem de SINAL_JWT_SEGREDO e precisa de
// ao menos 32 bytes (256 bits); sem ele a aplicação não sobe (segredos fora do repositório, Etapa 4 §20).
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    public static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;
    public static final String EMISSOR = "sinal";

    static SecretKey chave(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException("sinal.seguranca.jwt.segredo deve ter ao menos "
                    + TAMANHO_MINIMO_SEGREDO_BYTES + " bytes.");
        }
        return new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    SecretKey chaveJwt(@Value("${sinal.seguranca.jwt.segredo}") String segredo) {
        return chave(segredo);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveJwt) {
        return NimbusJwtEncoder.withSecretKey(chaveJwt).algorithm(MacAlgorithm.HS256).build();
    }

    // Somente HS256; expiração sem tolerância (emissor e validador usam o mesmo Clock) e emissor obrigatório.
    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveJwt, Clock clock) {
        return decodificador(chaveJwt, clock);
    }

    static NimbusJwtDecoder decodificador(SecretKey chave, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator expiracao = new JwtTimestampValidator(Duration.ZERO);
        expiracao.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(expiracao, new JwtIssuerValidator(EMISSOR)));
        return decoder;
    }
}
