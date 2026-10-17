package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.chamado.application.query.ChamadoHistoricoItem;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.time.Instant;

// Item do Histórico; ocorrencia é nula na Emergência Imediata. codigoSimulacao formatado aqui (Etapa 5 N3).
public record ChamadoHistoricoResponse(Long id, String codigoSimulacao, ModalidadeChamado modalidade,
                                       OrgaoSeguranca orgao, Ocorrencia ocorrencia, EstadoChamado estado,
                                       Boolean baixaPrecisao, Instant dataCriacao) {

    public record Ocorrencia(Long id, String nome) {
    }

    static ChamadoHistoricoResponse de(ChamadoHistoricoItem item) {
        Ocorrencia ocorrencia = item.ocorrenciaId() == null ? null
                : new Ocorrencia(item.ocorrenciaId(), item.ocorrenciaNome());
        return new ChamadoHistoricoResponse(item.id(), "#SIM-" + item.id(), item.modalidade(), item.orgao(),
                ocorrencia, item.estado(), item.baixaPrecisao(), item.dataCriacao());
    }
}
