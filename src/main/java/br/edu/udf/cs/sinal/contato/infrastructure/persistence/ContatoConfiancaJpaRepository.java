package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContatoConfiancaJpaRepository extends JpaRepository<ContatoConfiancaJpaEntity, Long> {

    // O id auto-incremental reproduz a ordem do agregado: novos ao fim, edições na mesma posição.
    List<ContatoConfiancaJpaEntity> findByUsuarioIdOrderByIdAsc(Long usuarioId);

    // Serializa as alterações de contatos do mesmo usuário até o fim da transação (limite CT1).
    @Query(value = "SELECT id FROM usuario WHERE id = :usuarioId FOR UPDATE", nativeQuery = true)
    Optional<Long> bloquearUsuario(@Param("usuarioId") Long usuarioId);
}
