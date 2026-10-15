package br.edu.udf.cs.sinal.encaminhamento.domain.gateway;

import java.time.Instant;

// Não decide transições do Chamado, validação de ocorrência nem idempotência; a Application conduz o fluxo.
public interface EncaminhamentoGateway {

    ResultadoEncaminhamento encaminhar(SolicitacaoEncaminhamento solicitacao, Instant referencia);

    // Próxima etapa do agendamento; no modo simulado, aplica a duração configurada no adapter.
    Instant agendarProximaEtapa(Long chamadoId, Instant referencia);
}
