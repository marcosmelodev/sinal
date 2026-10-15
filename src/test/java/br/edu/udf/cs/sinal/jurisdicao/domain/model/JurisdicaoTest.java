package br.edu.udf.cs.sinal.jurisdicao.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JurisdicaoTest {

    private static final String CODIGO = "JURISDICAO-FICTICIA-01";
    private static final String NOME = "Jurisdição Fictícia 01";

    private static final CentralAtendimento CENTRAL_PM =
            new CentralAtendimento("C-PM", "Central Fictícia PM", OrgaoSeguranca.POLICIA_MILITAR);
    private static final CentralAtendimento CENTRAL_CBM =
            new CentralAtendimento("C-CBM", "Central Fictícia CBM", OrgaoSeguranca.CORPO_DE_BOMBEIROS);
    private static final CentralAtendimento CENTRAL_SAMU =
            new CentralAtendimento("C-SAMU", "Central Fictícia SAMU", OrgaoSeguranca.SAMU);

    private static Jurisdicao comCentrais(List<CentralAtendimento> centrais) {
        return new Jurisdicao(CODIGO, NOME, centrais);
    }

    @Test
    void deveCriarPreservandoOsValores() {
        Jurisdicao jurisdicao = new Jurisdicao(" " + CODIGO + " ", "  " + NOME, List.of(CENTRAL_PM, CENTRAL_SAMU));

        assertThat(jurisdicao.getCodigo()).isEqualTo(CODIGO);
        assertThat(jurisdicao.getNome()).isEqualTo(NOME);
        assertThat(jurisdicao.getCentrais()).containsExactly(CENTRAL_PM, CENTRAL_SAMU);
    }

    @ParameterizedTest
    @EnumSource(OrgaoSeguranca.class)
    void centralParaDeveDevolverACentralDoOrgao(OrgaoSeguranca orgao) {
        Jurisdicao jurisdicao = comCentrais(List.of(CENTRAL_PM, CENTRAL_CBM, CENTRAL_SAMU));

        assertThat(jurisdicao.centralPara(orgao)).hasValueSatisfying(c -> assertThat(c.getOrgao()).isEqualTo(orgao));
    }

    @Test
    void centralParaDeveSerVaziaQuandoOOrgaoNaoTemCentral() {
        Jurisdicao jurisdicao = comCentrais(List.of(CENTRAL_PM));

        assertThat(jurisdicao.centralPara(OrgaoSeguranca.SAMU)).isEmpty();
        assertThat(jurisdicao.centralPara(OrgaoSeguranca.POLICIA_MILITAR)).contains(CENTRAL_PM);
    }

    @Test
    void deveAceitarJurisdicaoSemCentrais() {
        Jurisdicao jurisdicao = comCentrais(List.of());

        assertThat(jurisdicao.getCentrais()).isEmpty();
        assertThat(jurisdicao.centralPara(OrgaoSeguranca.POLICIA_MILITAR)).isEmpty();
    }

    @Test
    void centralParaDeveRejeitarOrgaoNulo() {
        Jurisdicao jurisdicao = comCentrais(List.of(CENTRAL_PM));

        assertThatThrownBy(() -> jurisdicao.centralPara(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarCodigoAusente(String codigo) {
        assertThatThrownBy(() -> new Jurisdicao(codigo, NOME, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void deveRejeitarNomeAusente(String nome) {
        assertThatThrownBy(() -> new Jurisdicao(CODIGO, nome, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarListaDeCentraisNula() {
        assertThatThrownBy(() -> comCentrais(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarCentralNula() {
        List<CentralAtendimento> centrais = new ArrayList<>();
        centrais.add(CENTRAL_PM);
        centrais.add(null);

        assertThatThrownBy(() -> comCentrais(centrais)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarDuasCentraisDoMesmoOrgao() {
        CentralAtendimento outraPm = new CentralAtendimento("C-PM-2", "Outra Central PM", OrgaoSeguranca.POLICIA_MILITAR);

        assertThatThrownBy(() -> comCentrais(List.of(CENTRAL_PM, CENTRAL_SAMU, outraPm)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoDeveCompartilharAListaRecebida() {
        List<CentralAtendimento> centrais = new ArrayList<>(List.of(CENTRAL_PM));
        Jurisdicao jurisdicao = comCentrais(centrais);

        centrais.add(CENTRAL_SAMU);

        assertThat(jurisdicao.getCentrais()).containsExactly(CENTRAL_PM);
        assertThat(jurisdicao.centralPara(OrgaoSeguranca.SAMU)).isEmpty();
    }

    @Test
    void listaDeCentraisNaoDeveSerModificavel() {
        Jurisdicao jurisdicao = comCentrais(List.of(CENTRAL_PM));

        assertThatThrownBy(() -> jurisdicao.getCentrais().add(CENTRAL_SAMU))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> jurisdicao.getCentrais().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deveSerIgualPorValor() {
        Jurisdicao a = comCentrais(List.of(CENTRAL_PM, CENTRAL_SAMU));
        Jurisdicao b = comCentrais(new ArrayList<>(List.of(CENTRAL_PM, CENTRAL_SAMU)));

        assertThat(a).isNotSameAs(b).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void deveSerDiferenteQuandoAlgumCampoDifere() {
        Jurisdicao base = comCentrais(List.of(CENTRAL_PM));

        assertThat(base).isNotEqualTo(new Jurisdicao("OUTRA", NOME, List.of(CENTRAL_PM)));
        assertThat(base).isNotEqualTo(new Jurisdicao(CODIGO, "Outra", List.of(CENTRAL_PM)));
        assertThat(base).isNotEqualTo(comCentrais(List.of(CENTRAL_SAMU)));
        assertThat(base).isNotEqualTo(comCentrais(List.of()));
    }

    @Test
    void deveSerReflexivaENaoIgualANuloOuOutroTipo() {
        Jurisdicao jurisdicao = comCentrais(List.of());

        assertThat(jurisdicao).isEqualTo(jurisdicao);
        assertThat(jurisdicao.equals(null)).isFalse();
        assertThat(jurisdicao.equals(CODIGO)).isFalse();
    }
}
