package br.edu.udf.cs.sinal.jurisdicao.infrastructure;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.CentralAtendimento;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.Jurisdicao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JurisdicaoMockAdapterTest {

    private final JurisdicaoMockAdapter adapter = new JurisdicaoMockAdapter();

    private static Localizacao em(double latitude, double longitude) {
        return new Localizacao(latitude, longitude, 25.0f, Instant.parse("2026-10-17T12:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(OrgaoSeguranca.class)
    void dentroDoDfDeveDevolverAJurisdicaoFicticiaComACentralDoOrgao(OrgaoSeguranca orgao) {
        Jurisdicao jurisdicao = adapter.consultar(em(-15.7939, -47.8828), orgao).orElseThrow();

        assertThat(jurisdicao.getCodigo()).isEqualTo("JURISDICAO-FICTICIA-DF");
        assertThat(jurisdicao.centralPara(orgao)).hasValueSatisfying(central -> {
            assertThat(central.getOrgao()).isEqualTo(orgao);
            assertThat(central.getCodigo()).startsWith("CENTRAL-FICTICIA-DF-");
        });
    }

    @ParameterizedTest
    @CsvSource({"-16.06, -48.29", "-15.50, -47.31", "-16.06, -47.31", "-15.50, -48.29"})
    void limitesDaAreaDevemSerIncluidos(double latitude, double longitude) {
        assertThat(adapter.consultar(em(latitude, longitude), OrgaoSeguranca.SAMU)).isPresent();
    }

    @ParameterizedTest
    @CsvSource({"-23.5505, -46.6333", "-16.0601, -47.88", "-15.4999, -47.88", "-15.79, -48.2901", "-15.79, -47.3099"})
    void foraDaAreaNaoDeveHaverJurisdicao(double latitude, double longitude) {
        assertThat(adapter.consultar(em(latitude, longitude), OrgaoSeguranca.SAMU)).isEmpty();
    }

    @Test
    void localizacaoNulaNaoDeveTerJurisdicao() {
        assertThat(adapter.consultar(null, OrgaoSeguranca.SAMU)).isEmpty();
    }

    @Test
    void nomesDevemSerExplicitamenteFicticios() {
        Jurisdicao jurisdicao = JurisdicaoMockAdapter.JURISDICAO_FICTICIA_DF;

        assertThat(jurisdicao.getNome()).containsIgnoringCase("fictícia").contains("simulação");
        assertThat(jurisdicao.getCentrais()).hasSize(3).extracting(CentralAtendimento::getNome)
                .allSatisfy(nome -> assertThat(nome).containsIgnoringCase("fictícia").contains("simulação"));
    }
}
