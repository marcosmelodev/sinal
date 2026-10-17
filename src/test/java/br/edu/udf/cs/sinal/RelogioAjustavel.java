package br.edu.udf.cs.sinal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

// Relógio de teste em UTC, com resolução de microssegundos como o ClockConfig, avançado manualmente.
public class RelogioAjustavel extends Clock {

    private final AtomicReference<Instant> agora;

    public RelogioAjustavel(Instant inicio) {
        this.agora = new AtomicReference<>(inicio.truncatedTo(ChronoUnit.MICROS));
    }

    public void avancar(Duration duracao) {
        agora.updateAndGet(instante -> instante.plus(duracao));
    }

    public void definir(Instant instante) {
        agora.set(instante.truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public Instant instant() {
        return agora.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("O relógio de teste opera somente em UTC.");
    }
}
