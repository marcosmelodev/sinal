package br.edu.udf.cs.sinal.compartilhado.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DominioExceptionTest {

    @Test
    void devePreservarCodigoEMensagem() {
        DominioException ex = new DominioException("CODIGO_TESTE", "Mensagem de teste.");

        assertThat(ex.getCodigo()).isEqualTo("CODIGO_TESTE");
        assertThat(ex.getMensagem()).isEqualTo("Mensagem de teste.");
        assertThat(ex.getMessage()).isEqualTo("Mensagem de teste.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarCodigoNuloOuVazio(String codigo) {
        assertThatThrownBy(() -> new DominioException(codigo, "Mensagem de teste."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveSerRuntimeException() {
        assertThat(new DominioException("CODIGO_TESTE", "Mensagem de teste."))
                .isInstanceOf(RuntimeException.class);
    }
}
