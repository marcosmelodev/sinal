package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EncaminhamentoJpaRepository extends JpaRepository<EncaminhamentoJpaEntity, Long> {

    Optional<EncaminhamentoJpaEntity> findByChamadoId(Long chamadoId);

    // Serializa alterações concorrentes do Encaminhamento do Chamado até o fim da transação.
    @Query(value = "SELECT id FROM encaminhamento WHERE chamado_id = :chamadoId FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearPorChamadoId(@Param("chamadoId") Long chamadoId);

    // Consulta do job, apoiada no índice (status, proxima_transicao_em); sem bloqueio (T1).
    List<EncaminhamentoJpaEntity> findByStatusAndProximaTransicaoEmLessThanEqualOrderByProximaTransicaoEmAscIdAsc(
            StatusEncaminhamento status, Instant referencia, Limit limite);
}
