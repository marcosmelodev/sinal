package br.edu.udf.cs.sinal.contato.domain.repository;

import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;

public interface ContatosRepository {

    // Leitura sem bloqueio. Nunca devolve nulo: sem contatos persistidos, devolve um agregado vazio do usuário.
    ContatosDeConfianca buscarPorUsuario(Long usuarioId);

    // Para adicionar, editar ou remover: exige transação ativa e bloqueia alterações concorrentes
    // dos contatos do mesmo usuário até o fim dela, protegendo o limite de 5 (CT1).
    ContatosDeConfianca buscarPorUsuarioParaAlteracao(Long usuarioId);

    // Sincroniza os contatos preservando os ids existentes; devolve o agregado com os ids gerados.
    ContatosDeConfianca salvar(ContatosDeConfianca contatos);
}
