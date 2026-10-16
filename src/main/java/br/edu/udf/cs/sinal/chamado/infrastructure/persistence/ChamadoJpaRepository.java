package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// As buscas carregam as transições na mesma consulta (EntityGraph), evitando uma consulta por Chamado.
public interface ChamadoJpaRepository extends JpaRepository<ChamadoJpaEntity, Long> {

    @EntityGraph(attributePaths = "transicoes")
    Optional<ChamadoJpaEntity> findWithTransicoesById(Long id);

    @EntityGraph(attributePaths = "transicoes")
    Optional<ChamadoJpaEntity> findByUsuarioIdAndChaveIdempotencia(Long usuarioId, String chaveIdempotencia);

    @EntityGraph(attributePaths = "transicoes")
    List<ChamadoJpaEntity> findByIdIn(Collection<Long> ids);

    // Serializa transições concorrentes do mesmo Chamado até o fim da transação.
    @Query(value = "SELECT id FROM chamado WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquear(@Param("id") Long id);

    // Reconciliação: consulta somente a existência de encaminhamento (outro módulo), sem mapeá-lo.
    @Query(value = """
            SELECT c.id FROM chamado c
            WHERE c.estado = 'CRIADO' AND c.data_criacao < :criadosAntesDe
              AND NOT EXISTS (SELECT 1 FROM encaminhamento e WHERE e.chamado_id = c.id)
            ORDER BY c.data_criacao, c.id
            LIMIT :limite""", nativeQuery = true)
    List<Long> idsCriadosSemEncaminhamento(@Param("criadosAntesDe") Instant criadosAntesDe,
                                           @Param("limite") int limite);
}
