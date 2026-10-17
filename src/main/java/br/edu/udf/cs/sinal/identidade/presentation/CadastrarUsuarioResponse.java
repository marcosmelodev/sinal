package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.identidade.application.UsuarioCadastrado;

import java.time.Instant;

public record CadastrarUsuarioResponse(Long id, String nome, String cpfMascarado, String email, Instant dataCadastro,
                                       Boolean ativo) {

    static CadastrarUsuarioResponse de(UsuarioCadastrado usuario) {
        return new CadastrarUsuarioResponse(usuario.id(), usuario.nome(), usuario.cpfMascarado(), usuario.email(),
                usuario.dataCadastro(), usuario.ativo());
    }
}
