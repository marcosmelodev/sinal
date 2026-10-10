package br.edu.udf.cs.sinal.insfrastructure.persistence.repository;

import br.edu.udf.cs.sinal.insfrastructure.persistence.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataUsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByCpf(String cpf);
    Optional<UsuarioEntity> findByEmail(String email);
}
