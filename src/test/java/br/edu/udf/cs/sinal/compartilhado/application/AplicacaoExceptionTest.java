package br.edu.udf.cs.sinal.compartilhado.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AplicacaoExceptionTest {

    @Test
    void devePreservarCodigoMensagemECampos() {
        AplicacaoException ex = new AplicacaoException("CADASTRO_JA_EXISTENTE", "Já existe.", Set.of("cpf", "email"));

        assertThat(ex.getCodigo()).isEqualTo("CADASTRO_JA_EXISTENTE");
        assertThat(ex.getMensagem()).isEqualTo("Já existe.");
        assertThat(ex.getMessage()).isEqualTo("Já existe.");
        assertThat(ex.getCampos()).containsExactlyInAnyOrder("cpf", "email");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarCodigoAusente(String codigo) {
        assertThatThrownBy(() -> new AplicacaoException(codigo, "Mensagem.", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void camposNulosDevemVirarConjuntoVazio() {
        assertThat(new AplicacaoException("CODIGO", "Mensagem.", null).getCampos()).isEmpty();
    }

    @Test
    void camposNaoDevemSerAlteraveisDeFora() {
        Set<String> campos = new HashSet<>(Set.of("cpf"));
        AplicacaoException ex = new AplicacaoException("CODIGO", "Mensagem.", campos);

        campos.add("email");

        assertThat(ex.getCampos()).containsExactly("cpf");
        assertThatThrownBy(() -> ex.getCampos().add("senha")).isInstanceOf(UnsupportedOperationException.class);
    }
}
