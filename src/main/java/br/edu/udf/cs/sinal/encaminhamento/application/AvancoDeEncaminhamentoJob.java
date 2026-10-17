package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.repository.EncaminhamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

// Job persistente da simulação (Etapa 4 §18): avança os encaminhamentos vencidos e reconcilia Chamados CRIADO sem
// Encaminhamento (evento perdido). Busca sem bloqueio; cada item é processado e reconferido na própria transação,
// então várias instâncias podem rodar ao mesmo tempo sem aplicar uma etapa duas vezes.
@Component
public class AvancoDeEncaminhamentoJob {

    private static final Logger LOG = LoggerFactory.getLogger(AvancoDeEncaminhamentoJob.class);

    public record ResultadoDoCiclo(int avancados, int iniciados, int falhas) {
    }

    private final EncaminhamentoRepository encaminhamentoRepository;
    private final ChamadoRepository chamadoRepository;
    private final EncaminhamentoService encaminhamentoService;
    private final Clock clock;
    private final int lote;
    private final Duration carenciaDaReconciliacao;

    public AvancoDeEncaminhamentoJob(EncaminhamentoRepository encaminhamentoRepository,
                                     ChamadoRepository chamadoRepository, EncaminhamentoService encaminhamentoService,
                                     Clock clock, @Value("${sinal.simulacao.job.lote}") int lote,
                                     @Value("${sinal.simulacao.reconciliacao.carencia}") String carenciaDaReconciliacao) {
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.chamadoRepository = chamadoRepository;
        this.encaminhamentoService = encaminhamentoService;
        this.clock = clock;
        this.lote = lote;
        this.carenciaDaReconciliacao = Duration.parse(carenciaDaReconciliacao);
    }

    // Ativado somente com sinal.simulacao.job.habilitado=true (AgendamentoConfig).
    @Scheduled(fixedDelayString = "${sinal.simulacao.job.intervalo}")
    public void executarAgendado() {
        executar();
    }

    public ResultadoDoCiclo executar() {
        Instant agora = Instant.now(clock);
        List<Long> vencidos = encaminhamentoRepository.buscarAgendadosVencidos(agora, lote).stream()
                .map(Encaminhamento::getChamadoId).toList();
        int[] falhas = {0};
        int avancados = processar(vencidos, encaminhamentoService::avancar, falhas, "avançar");
        // A carência evita disputar com o ouvinte de um Chamado recém-registrado.
        List<Long> semEncaminhamento = chamadoRepository
                .buscarCriadosSemEncaminhamento(agora.minus(carenciaDaReconciliacao), lote).stream()
                .map(Chamado::getId).toList();
        int iniciados = processar(semEncaminhamento, encaminhamentoService::iniciar, falhas, "reconciliar");
        return new ResultadoDoCiclo(avancados, iniciados, falhas[0]);
    }

    // A falha de um item é isolada: o restante do lote continua e o item volta no próximo ciclo.
    private static int processar(List<Long> chamadoIds, Predicate<Long> operacao, int[] falhas, String descricao) {
        int aplicados = 0;
        for (Long chamadoId : chamadoIds) {
            try {
                if (operacao.test(chamadoId)) {
                    aplicados++;
                }
            } catch (RuntimeException e) {
                falhas[0]++;
                LOG.warn("Falha ao {} o encaminhamento do chamado {} ({}).", descricao, chamadoId,
                        e.getClass().getName());
            }
        }
        return aplicados;
    }
}
