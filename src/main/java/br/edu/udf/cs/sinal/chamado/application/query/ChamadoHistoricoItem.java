package br.edu.udf.cs.sinal.chamado.application.query;

import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.time.Instant;

// Item do Histórico (Etapa 3 §12): órgão, ocorrência, data/hora, status e a sinalização de baixa precisão
// (Etapa 5 §7). Localização, detalhe e transições ficam para os Detalhes. Ocorrência nula na Emergência Imediata.
public record ChamadoHistoricoItem(Long id, ModalidadeChamado modalidade, OrgaoSeguranca orgao, Long ocorrenciaId,
                                   String ocorrenciaNome, EstadoChamado estado, Boolean baixaPrecisao,
                                   Instant dataCriacao) {
}
