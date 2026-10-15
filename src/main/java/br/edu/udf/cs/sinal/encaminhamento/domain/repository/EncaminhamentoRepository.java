package br.edu.udf.cs.sinal.encaminhamento.domain.repository;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EncaminhamentoRepository {

    Encaminhamento salvar(Encaminhamento encaminhamento);

    // Apoio ao EN1 (no máximo um Encaminhamento por Chamado), junto da restrição única no banco.
    Optional<Encaminhamento> buscarPorChamadoId(Long chamadoId);

    // Encaminhamentos AGENDADO com proximaTransicaoEm até a referência, para o job.
    List<Encaminhamento> buscarAgendadosVencidos(Instant referencia, int limite);
}
