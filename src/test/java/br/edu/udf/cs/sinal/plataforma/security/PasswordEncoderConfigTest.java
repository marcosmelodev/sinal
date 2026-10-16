package br.edu.udf.cs.sinal.plataforma.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void deveGerarHashBcryptComForca12QueConfereComASenha() {
        String hash = encoder.encode("senha-segura");

        assertThat(hash).startsWith("$2a$12$").doesNotContain("senha-segura");
        assertThat(encoder.matches("senha-segura", hash)).isTrue();
        assertThat(encoder.matches("outra-senha", hash)).isFalse();
    }

    @Test
    void mesmaSenhaDeveGerarHashesDiferentes() {
        assertThat(encoder.encode("senha-segura")).isNotEqualTo(encoder.encode("senha-segura"));
    }

    // Documenta o limite do BCrypt na versão em uso: a política de senha deve barrar antes do hash.
    @Test
    void deveRejeitarSenhaComMaisDe72Bytes() {
        assertThatThrownBy(() -> encoder.encode("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
    }
}
