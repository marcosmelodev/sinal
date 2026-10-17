package br.edu.udf.cs.sinal.encaminhamento.infrastructure.simulacao;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.ResultadoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.SolicitacaoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncaminhamentoSimuladoAdapterTest {

    private static final Instant REFERENCIA = Instant.parse("2026-10-17T12:00:00.123456Z");

    private final EncaminhamentoSimuladoAdapter adapter = new EncaminhamentoSimuladoAdapter("PT8S", "PT20S");

    @Test
    void encaminharDeveSerSimuladoSemProtocoloEComOAtrasoDeEncaminhamento() {
        SolicitacaoEncaminhamento solicitacao = new SolicitacaoEncaminhamento(1L, OrgaoSeguranca.SAMU, 12L,
                new Localizacao(-15.79, -47.88, 25.0f, REFERENCIA), "CENTRAL-FICTICIA-DF-SAMU");

        ResultadoEncaminhamento resultado = adapter.encaminhar(solicitacao, REFERENCIA);

        assertThat(resultado.modo()).isEqualTo(ModoEncaminhamento.SIMULADO);
        assertThat(resultado.protocoloExterno()).isNull();
        assertThat(resultado.proximaTransicaoEm()).isEqualTo(REFERENCIA.plusSeconds(8));
    }

    @Test
    void proximaEtapaDeveUsarOAtrasoDeEncerramento() {
        assertThat(adapter.agendarProximaEtapa(1L, REFERENCIA)).isEqualTo(REFERENCIA.plusSeconds(20));
    }

    @Test
    void atrasosDevemVirDaConfiguracao() {
        EncaminhamentoSimuladoAdapter rapido = new EncaminhamentoSimuladoAdapter("PT0.5S", "PT1M");

        assertThat(rapido.agendarProximaEtapa(1L, REFERENCIA)).isEqualTo(REFERENCIA.plusSeconds(60));
    }

    @Test
    void atrasoNegativoDeveSerRecusado() {
        assertThatThrownBy(() -> new EncaminhamentoSimuladoAdapter("PT-1S", "PT20S"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
