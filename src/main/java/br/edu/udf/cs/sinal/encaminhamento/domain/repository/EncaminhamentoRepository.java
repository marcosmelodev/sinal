package br.edu.udf.cs.sinal.encaminhamento.domain.repository;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Ordem de bloqueio: sempre bloquear o Chamado (ChamadoRepository.buscarPorIdParaAlteracao) antes do
// Encaminhamento dele, evitando deadlock entre transações que alteram os dois.
public interface EncaminhamentoRepository {

    // id nulo: insere (um segundo Encaminhamento para o mesmo Chamado viola UNIQUE(chamado_id), EN1);
    // com id: atualiza somente status e proximaTransicaoEm.
    Encaminhamento salvar(Encaminhamento encaminhamento);

    // Apoio ao EN1 (no máximo um Encaminhamento por Chamado), junto da restrição única no banco.
    Optional<Encaminhamento> buscarPorChamadoId(Long chamadoId);

    // Exige transação ativa e bloqueia alterações concorrentes até o fim dela; bloquear o Chamado antes.
    Optional<Encaminhamento> buscarPorChamadoIdParaAlteracao(Long chamadoId);

    // Encaminhamentos AGENDADO com proximaTransicaoEm até a referência (inclusive), para o job; sem bloqueio:
    // cada item deve ser reconferido em sua própria transação com buscarPorChamadoIdParaAlteracao.
    List<Encaminhamento> buscarAgendadosVencidos(Instant referencia, int limite);
}
