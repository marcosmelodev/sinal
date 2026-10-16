package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.repository.EncaminhamentoRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Segundo Encaminhamento para o mesmo Chamado (EN1) propaga como DataIntegrityViolationException;
// a Application o trata como já existente, relendo em nova transação.
@Repository
public class EncaminhamentoPersistenceAdapter implements EncaminhamentoRepository {

    private final EncaminhamentoJpaRepository repository;

    public EncaminhamentoPersistenceAdapter(EncaminhamentoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Encaminhamento salvar(Encaminhamento encaminhamento) {
        if (encaminhamento.getId() == null) {
            EncaminhamentoJpaEntity salvo = repository.saveAndFlush(
                    EncaminhamentoPersistenceMapper.toNovaEntidade(encaminhamento));
            return EncaminhamentoPersistenceMapper.toDomain(salvo);
        }
        EncaminhamentoJpaEntity entity = repository.findById(encaminhamento.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "O Encaminhamento " + encaminhamento.getId() + " não existe."));
        validarCoerencia(entity, encaminhamento);
        entity.atualizarAgendamento(encaminhamento.getStatus(), encaminhamento.getProximaTransicaoEm());
        repository.flush();
        return EncaminhamentoPersistenceMapper.toDomain(entity);
    }

    // Recusa um objeto desatualizado ou de outro Chamado; CONCLUIDO gravado não volta a AGENDADO (EN3).
    private static void validarCoerencia(EncaminhamentoJpaEntity gravado, Encaminhamento emMemoria) {
        if (!gravado.getChamadoId().equals(emMemoria.getChamadoId())) {
            throw new IllegalStateException("O Encaminhamento " + gravado.getId() + " pertence a outro Chamado.");
        }
        if (gravado.getStatus() == StatusEncaminhamento.CONCLUIDO
                && emMemoria.getStatus() == StatusEncaminhamento.AGENDADO) {
            throw new IllegalStateException("O Encaminhamento " + gravado.getId()
                    + " já foi concluído e não pode voltar a AGENDADO.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Encaminhamento> buscarPorChamadoId(Long chamadoId) {
        return repository.findByChamadoId(chamadoId).map(EncaminhamentoPersistenceMapper::toDomain);
    }

    // MANDATORY: sem transação o bloqueio seria liberado imediatamente, sem serializar as alterações.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Encaminhamento> buscarPorChamadoIdParaAlteracao(Long chamadoId) {
        repository.bloquearPorChamadoId(chamadoId);
        return repository.findByChamadoId(chamadoId).map(EncaminhamentoPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Encaminhamento> buscarAgendadosVencidos(Instant referencia, int limite) {
        if (referencia == null || limite <= 0) {
            throw new IllegalArgumentException("A referência é obrigatória e o limite deve ser positivo.");
        }
        return repository.findByStatusAndProximaTransicaoEmLessThanEqualOrderByProximaTransicaoEmAscIdAsc(
                        StatusEncaminhamento.AGENDADO, referencia, Limit.of(limite)).stream()
                .map(EncaminhamentoPersistenceMapper::toDomain)
                .toList();
    }
}
