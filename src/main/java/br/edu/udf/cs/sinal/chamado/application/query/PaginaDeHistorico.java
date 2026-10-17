package br.edu.udf.cs.sinal.chamado.application.query;

import java.util.List;

// proximoCursor nulo indica que não há mais itens.
public record PaginaDeHistorico(List<ChamadoHistoricoItem> itens, String proximoCursor) {

    public PaginaDeHistorico {
        itens = List.copyOf(itens);
    }
}
