package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.jurisdicao.domain.gateway.JurisdicaoGateway;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.CentralAtendimento;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.Jurisdicao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterminacaoDeCentralServiceTest {

    private static final Localizacao LOCALIZACAO =
            new Localizacao(-15.79, -47.88, 25.0f, Instant.parse("2026-10-17T12:00:00Z"));
    private static final Jurisdicao SOMENTE_PM = new Jurisdicao("JUR-1", "Jurisdição fictícia",
            List.of(new CentralAtendimento("C-PM", "Central fictícia PM", OrgaoSeguranca.POLICIA_MILITAR)));

    private JurisdicaoGateway gateway;
    private DeterminacaoDeCentralService service;

    @BeforeEach
    void configurar() {
        gateway = mock(JurisdicaoGateway.class);
        service = new DeterminacaoDeCentralService(gateway);
    }

    @Test
    void deveDevolverAJurisdicaoEACentralDoOrgao() {
        when(gateway.consultar(LOCALIZACAO, OrgaoSeguranca.POLICIA_MILITAR)).thenReturn(Optional.of(SOMENTE_PM));

        assertThat(service.determinar(LOCALIZACAO, OrgaoSeguranca.POLICIA_MILITAR))
                .isEqualTo(new CentralDeterminada("JUR-1", "C-PM"));
    }

    @Test
    void jurisdicaoSemCentralDoOrgaoDeveManterSomenteAJurisdicao() {
        when(gateway.consultar(LOCALIZACAO, OrgaoSeguranca.SAMU)).thenReturn(Optional.of(SOMENTE_PM));

        assertThat(service.determinar(LOCALIZACAO, OrgaoSeguranca.SAMU)).isEqualTo(new CentralDeterminada("JUR-1", null));
    }

    @Test
    void semJurisdicaoDeveDevolverNenhuma() {
        when(gateway.consultar(any(), any())).thenReturn(Optional.empty());

        assertThat(service.determinar(LOCALIZACAO, OrgaoSeguranca.SAMU)).isEqualTo(CentralDeterminada.NENHUMA);
    }

    @Test
    void falhaTecnicaDoGatewayNaoDevePropagar() {
        when(gateway.consultar(any(), any())).thenThrow(new IllegalStateException("fonte indisponível"));

        assertThat(service.determinar(LOCALIZACAO, OrgaoSeguranca.SAMU)).isEqualTo(CentralDeterminada.NENHUMA);
    }
}
