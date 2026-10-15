package br.edu.udf.cs.sinal.encaminhamento.domain.gateway;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitacaoEncaminhamentoTest {

    private static final Localizacao LOCALIZACAO =
            new Localizacao(-15.7939, -47.8828, 25.0f, Instant.parse("2026-10-15T12:00:00Z"));

    @Test
    void deveCriarSolicitacaoCompleta() {
        SolicitacaoEncaminhamento solicitacao =
                new SolicitacaoEncaminhamento(1L, OrgaoSeguranca.SAMU, 10L, LOCALIZACAO, "C-SAMU");

        assertThat(solicitacao.chamadoId()).isEqualTo(1L);
        assertThat(solicitacao.orgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(solicitacao.ocorrenciaId()).isEqualTo(10L);
        assertThat(solicitacao.localizacao()).isEqualTo(LOCALIZACAO);
        assertThat(solicitacao.centralCodigo()).isEqualTo("C-SAMU");
    }

    @Test
    void deveAceitarEmergenciaImediataSemOcorrenciaNemCentral() {
        SolicitacaoEncaminhamento solicitacao =
                new SolicitacaoEncaminhamento(1L, OrgaoSeguranca.POLICIA_MILITAR, null, LOCALIZACAO, null);

        assertThat(solicitacao.ocorrenciaId()).isNull();
        assertThat(solicitacao.centralCodigo()).isNull();
    }

    @Test
    void deveRejeitarChamadoNulo() {
        assertThatThrownBy(() -> new SolicitacaoEncaminhamento(null, OrgaoSeguranca.SAMU, 10L, LOCALIZACAO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarOrgaoNulo() {
        assertThatThrownBy(() -> new SolicitacaoEncaminhamento(1L, null, 10L, LOCALIZACAO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarLocalizacaoNula() {
        assertThatThrownBy(() -> new SolicitacaoEncaminhamento(1L, OrgaoSeguranca.SAMU, 10L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
