package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"curto", "1234567890123456789012345678901"})
    void segredoAusenteOuComMenosDe32BytesDeveImpedirAInicializacao(String segredo) {
        assertThatThrownBy(() -> JwtConfig.chave(segredo)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void segredoCom32BytesDeveGerarChaveHmacSha256() {
        assertThat(JwtConfig.chave("12345678901234567890123456789012").getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void tamanhoDeveSerMedidoEmBytes() {
        // 16 caracteres "ç" ocupam 32 bytes em UTF-8.
        assertThat(JwtConfig.chave("ç".repeat(16))).isNotNull();
    }
}
