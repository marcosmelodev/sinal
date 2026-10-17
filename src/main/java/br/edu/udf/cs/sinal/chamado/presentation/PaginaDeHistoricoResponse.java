package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.chamado.application.query.PaginaDeHistorico;

import java.util.List;

// proximoCursor nulo indica a última página; o valor deve ser repassado sem alteração no parâmetro cursor.
public record PaginaDeHistoricoResponse(List<ChamadoHistoricoResponse> itens, String proximoCursor) {

    static PaginaDeHistoricoResponse de(PaginaDeHistorico pagina) {
        return new PaginaDeHistoricoResponse(pagina.itens().stream().map(ChamadoHistoricoResponse::de).toList(),
                pagina.proximoCursor());
    }
}
