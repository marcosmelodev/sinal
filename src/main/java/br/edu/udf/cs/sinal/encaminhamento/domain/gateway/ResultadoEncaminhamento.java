package br.edu.udf.cs.sinal.encaminhamento.domain.gateway;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;

import java.time.Instant;

// protocoloExterno é sempre nulo no modo simulado.
public record ResultadoEncaminhamento(ModoEncaminhamento modo, Instant proximaTransicaoEm, String protocoloExterno) {

    public ResultadoEncaminhamento {
        if (modo == null) {
            throw new IllegalArgumentException("O modo de encaminhamento é obrigatório.");
        }
        if (proximaTransicaoEm == null) {
            throw new IllegalArgumentException("A próxima transição prevista é obrigatória.");
        }
    }
}
