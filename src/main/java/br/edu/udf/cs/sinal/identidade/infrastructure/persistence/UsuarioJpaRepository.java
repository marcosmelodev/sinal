package br.edu.udf.cs.sinal.identidade.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    Optional<UsuarioJpaEntity> findByCpf(String cpf);

    Optional<UsuarioJpaEntity> findByEmail(String email);
}
