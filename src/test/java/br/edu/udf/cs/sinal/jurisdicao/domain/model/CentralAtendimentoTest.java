package br.edu.udf.cs.sinal.jurisdicao.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CentralAtendimentoTest {

    private static final String CODIGO = "CENTRAL-FICTICIA-PM";
    private static final String NOME = "Central Fictícia da PM";

    @ParameterizedTest
    @EnumSource(OrgaoSeguranca.class)
    void deveCriarPreservandoOsValores(OrgaoSeguranca orgao) {
        CentralAtendimento central = new CentralAtendimento(CODIGO, NOME, orgao);

        assertThat(central.getCodigo()).isEqualTo(CODIGO);
        assertThat(central.getNome()).isEqualTo(NOME);
        assertThat(central.getOrgao()).isEqualTo(orgao);
    }

    @Test
    void deveApararCodigoENome() {
        CentralAtendimento central = new CentralAtendimento("  " + CODIGO + " ", " " + NOME + "  ",
                OrgaoSeguranca.SAMU);

        assertThat(central.getCodigo()).isEqualTo(CODIGO);
        assertThat(central.getNome()).isEqualTo(NOME);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarCodigoAusente(String codigo) {
        assertThatThrownBy(() -> new CentralAtendimento(codigo, NOME, OrgaoSeguranca.SAMU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarNomeAusente(String nome) {
        assertThatThrownBy(() -> new CentralAtendimento(CODIGO, nome, OrgaoSeguranca.SAMU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarOrgaoNulo() {
        assertThatThrownBy(() -> new CentralAtendimento(CODIGO, NOME, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveSerIgualPorValor() {
        CentralAtendimento a = new CentralAtendimento(CODIGO, NOME, OrgaoSeguranca.POLICIA_MILITAR);
        CentralAtendimento b = new CentralAtendimento(" " + CODIGO, NOME, OrgaoSeguranca.POLICIA_MILITAR);

        assertThat(a).isNotSameAs(b).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void deveSerDiferenteQuandoAlgumCampoDifere() {
        CentralAtendimento base = new CentralAtendimento(CODIGO, NOME, OrgaoSeguranca.POLICIA_MILITAR);

        assertThat(base).isNotEqualTo(new CentralAtendimento("OUTRO", NOME, OrgaoSeguranca.POLICIA_MILITAR));
        assertThat(base).isNotEqualTo(new CentralAtendimento(CODIGO, "Outro", OrgaoSeguranca.POLICIA_MILITAR));
        assertThat(base).isNotEqualTo(new CentralAtendimento(CODIGO, NOME, OrgaoSeguranca.SAMU));
    }

    @Test
    void deveSerReflexivaENaoIgualANuloOuOutroTipo() {
        CentralAtendimento central = new CentralAtendimento(CODIGO, NOME, OrgaoSeguranca.SAMU);

        assertThat(central).isEqualTo(central);
        assertThat(central.equals(null)).isFalse();
        assertThat(central.equals(CODIGO)).isFalse();
    }
}
