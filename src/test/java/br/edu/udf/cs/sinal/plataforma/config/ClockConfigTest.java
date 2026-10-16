package br.edu.udf.cs.sinal.plataforma.config;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    private final Clock clock = new ClockConfig().clock();

    @Test
    void deveUsarUtc() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @RepeatedTest(5)
    void instantesNaoDevemTerFracaoAbaixoDoMicrossegundo() {
        Instant agora = Instant.now(clock);

        assertThat(agora.getNano() % 1_000).isZero();
    }

    @Test
    void deveAcompanharORelogioDoSistema() {
        Instant antes = Instant.now().minusSeconds(1);

        assertThat(Instant.now(clock)).isAfter(antes).isBefore(Instant.now().plusSeconds(1));
    }
}
