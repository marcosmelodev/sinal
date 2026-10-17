package br.edu.udf.cs.sinal.encaminhamento.infrastructure.simulacao;

import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.EncaminhamentoGateway;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.ResultadoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.gateway.SolicitacaoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// Simulação (Etapa 4 §18): nenhum órgão real é acionado e não há protocolo externo. As durações são
// configuração, não regra de negócio.
@Component
public class EncaminhamentoSimuladoAdapter implements EncaminhamentoGateway {

    private final Duration atrasoEncaminhamento;
    private final Duration atrasoEncerramento;

    public EncaminhamentoSimuladoAdapter(@Value("${sinal.simulacao.atraso-encaminhamento}") String atrasoEncaminhamento,
                                         @Value("${sinal.simulacao.atraso-encerramento}") String atrasoEncerramento) {
        this.atrasoEncaminhamento = positivo(Duration.parse(atrasoEncaminhamento));
        this.atrasoEncerramento = positivo(Duration.parse(atrasoEncerramento));
    }

    // Etapa 1: CRIADO -> ENCAMINHADO_SIMULADO após o atraso de encaminhamento.
    @Override
    public ResultadoEncaminhamento encaminhar(SolicitacaoEncaminhamento solicitacao, Instant referencia) {
        return new ResultadoEncaminhamento(ModoEncaminhamento.SIMULADO, referencia.plus(atrasoEncaminhamento), null);
    }

    // Etapa 2: ENCAMINHADO_SIMULADO -> ENCERRADO após o atraso de encerramento.
    @Override
    public Instant agendarProximaEtapa(Long chamadoId, Instant referencia) {
        return referencia.plus(atrasoEncerramento);
    }

    private static Duration positivo(Duration duracao) {
        if (duracao.isNegative()) {
            throw new IllegalArgumentException("Os atrasos da simulação não podem ser negativos.");
        }
        return duracao;
    }
}
