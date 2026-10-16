package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Chave de idempotência repetida (UNIQUE) propaga como DataIntegrityViolationException; a Application a trata
// como replay, relendo o Chamado em nova transação.
@Repository
public class ChamadoPersistenceAdapter implements ChamadoRepository {

    private final ChamadoJpaRepository repository;

    public ChamadoPersistenceAdapter(ChamadoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Chamado salvarNovo(Chamado chamado, String chaveIdempotencia) {
        if (chamado.getId() != null) {
            throw new IllegalArgumentException("salvarNovo exige um Chamado ainda não persistido.");
        }
        if (chaveIdempotencia == null || chaveIdempotencia.isBlank()) {
            throw new IllegalArgumentException("A chave de idempotência é obrigatória.");
        }
        ChamadoJpaEntity salvo = repository.saveAndFlush(
                ChamadoPersistenceMapper.toNovaEntidade(chamado, chaveIdempotencia));
        return ChamadoPersistenceMapper.toDomain(salvo);
    }

    // Atualiza somente o estado e acrescenta as transições novas; o histórico gravado nunca é alterado.
    @Override
    @Transactional
    public Chamado salvar(Chamado chamado) {
        if (chamado.getId() == null) {
            throw new IllegalArgumentException("salvar exige um Chamado já persistido.");
        }
        ChamadoJpaEntity entity = repository.findWithTransicoesById(chamado.getId())
                .orElseThrow(() -> new IllegalStateException("O Chamado " + chamado.getId() + " não existe."));
        List<Transicao> novas = ChamadoPersistenceMapper.novasTransicoes(entity.getTransicoes(),
                chamado.getTransicoes());
        novas.forEach(transicao -> entity.acrescentarTransicao(ChamadoPersistenceMapper.toNovaTransicao(transicao)));
        entity.atualizarEstado(chamado.getEstado());
        repository.flush();
        return ChamadoPersistenceMapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chamado> buscarPorId(Long id) {
        return repository.findWithTransicoesById(id).map(ChamadoPersistenceMapper::toDomain);
    }

    // MANDATORY: sem transação o bloqueio seria liberado imediatamente, sem serializar as transições.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Chamado> buscarPorIdParaAlteracao(Long id) {
        repository.bloquear(id);
        return repository.findWithTransicoesById(id).map(ChamadoPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Chamado> buscarPorUsuarioEChaveIdempotencia(Long usuarioId, String chaveIdempotencia) {
        return repository.findByUsuarioIdAndChaveIdempotencia(usuarioId, chaveIdempotencia)
                .map(ChamadoPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Chamado> buscarCriadosSemEncaminhamento(Instant criadosAntesDe, int limite) {
        if (criadosAntesDe == null || limite <= 0) {
            throw new IllegalArgumentException("O corte é obrigatório e o limite deve ser positivo.");
        }
        List<Long> ids = repository.idsCriadosSemEncaminhamento(criadosAntesDe, limite);
        if (ids.isEmpty()) {
            return List.of();
        }
        // findByIdIn não garante ordem: reaplica a ordem da consulta (mais antigos primeiro).
        return repository.findByIdIn(ids).stream()
                .sorted(Comparator.comparingInt(entity -> ids.indexOf(entity.getId())))
                .map(ChamadoPersistenceMapper::toDomain)
                .toList();
    }
}
