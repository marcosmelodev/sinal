package br.edu.udf.cs.sinal.identidade.domain.repository;

import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    // Correspondência exata: o CPF deve estar canônico (somente os 11 dígitos).
    Optional<Usuario> buscarPorCpf(String cpf);

    // Correspondência exata: o e-mail deve estar canônico (aparado e em minúsculas).
    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(Long id);
}
