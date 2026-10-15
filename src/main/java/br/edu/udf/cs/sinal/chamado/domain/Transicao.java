package br.edu.udf.cs.sinal.chamado.domain;

import java.time.Instant;
import java.util.Objects;

public final class Transicao {

    private final EstadoChamado estado;
    private final Instant ocorridoEm;

    // Nulos aqui indicam erro de programação: o Chamado valida antes de criar a transição.
    public Transicao(EstadoChamado estado, Instant ocorridoEm) {
        if (estado == null) {
            throw new IllegalArgumentException("O estado da transição é obrigatório.");
        }
        if (ocorridoEm == null) {
            throw new IllegalArgumentException("O instante da transição é obrigatório.");
        }
        this.estado = estado;
        this.ocorridoEm = ocorridoEm;
    }

    public EstadoChamado getEstado() {
        return estado;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transicao outra)) {
            return false;
        }
        return estado == outra.estado && ocorridoEm.equals(outra.ocorridoEm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estado, ocorridoEm);
    }
}
