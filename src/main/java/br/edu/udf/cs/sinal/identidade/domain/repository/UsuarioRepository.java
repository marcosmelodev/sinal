package br.edu.udf.cs.sinal.identidade.domain.repository;

import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorCpf(String cpf);

    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(Long id);
}
