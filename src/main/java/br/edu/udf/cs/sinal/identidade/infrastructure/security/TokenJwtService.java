package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.identidade.application.EmissorDeToken;
import br.edu.udf.cs.sinal.identidade.application.TokenEmitido;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Access token de curta duração com claims mínimos: sub = id do usuário, sem dado pessoal (Etapa 4 §20).
@Component
public class TokenJwtService implements EmissorDeToken {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;
    private final Duration validade;

    public TokenJwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, Clock clock,
                           @Value("${sinal.seguranca.jwt.validade}") String validade) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
        this.validade = Duration.parse(validade);
        if (this.validade.isNegative() || this.validade.isZero()) {
            throw new IllegalStateException("sinal.seguranca.jwt.validade deve ser positiva.");
        }
    }

    @Override
    public TokenEmitido emitir(Long usuarioId) {
        Instant agora = Instant.now(clock);
        Instant expiraEm = agora.plus(validade);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.EMISSOR)
                .subject(String.valueOf(usuarioId))
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(token, expiraEm);
    }

    // Valida assinatura, algoritmo, emissor e expiração; lança JwtException se o token for inválido.
    public Long validarEExtrairUsuarioId(String token) {
        return Long.valueOf(jwtDecoder.decode(token).getSubject());
    }
}
