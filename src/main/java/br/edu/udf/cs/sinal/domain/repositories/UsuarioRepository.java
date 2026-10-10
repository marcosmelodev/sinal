package br.edu.udf.cs.sinal.domain.repositories;

import br.edu.udf.cs.sinal.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository  {

    Usuario salvar(Usuario usuario);
    Optional<Usuario> buscarPorCpf(String cpf);
    Optional<Usuario> buscarPorEmail(String email);
    Optional<Usuario> buscarPorId(Long id);
}
