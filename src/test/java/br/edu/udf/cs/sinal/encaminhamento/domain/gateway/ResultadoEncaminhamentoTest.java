package br.edu.udf.cs.sinal.encaminhamento.domain.gateway;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultadoEncaminhamentoTest {

    private static final Instant PROXIMA = Instant.parse("2026-10-15T12:00:08Z");

    @Test
    void deveCriarResultadoSimuladoSemProtocoloExterno() {
        ResultadoEncaminhamento resultado = new ResultadoEncaminhamento(ModoEncaminhamento.SIMULADO, PROXIMA, null);

        assertThat(resultado.modo()).isEqualTo(ModoEncaminhamento.SIMULADO);
        assertThat(resultado.proximaTransicaoEm()).isEqualTo(PROXIMA);
        assertThat(resultado.protocoloExterno()).isNull();
    }

    @Test
    void devePreservarProtocoloExternoQuandoInformado() {
        assertThat(new ResultadoEncaminhamento(ModoEncaminhamento.SIMULADO, PROXIMA, "PROTOCOLO-1").protocoloExterno())
                .isEqualTo("PROTOCOLO-1");
    }

    @Test
    void deveRejeitarModoNulo() {
        assertThatThrownBy(() -> new ResultadoEncaminhamento(null, PROXIMA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarProximaTransicaoNula() {
        assertThatThrownBy(() -> new ResultadoEncaminhamento(ModoEncaminhamento.SIMULADO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
