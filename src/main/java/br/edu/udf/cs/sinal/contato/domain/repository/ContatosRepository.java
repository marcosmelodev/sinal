package br.edu.udf.cs.sinal.contato.domain.repository;

import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;

public interface ContatosRepository {

    // Nunca devolve nulo: sem contatos persistidos, devolve um agregado vazio do usuário.
    ContatosDeConfianca buscarPorUsuario(Long usuarioId);

    ContatosDeConfianca salvar(ContatosDeConfianca contatos);
}
