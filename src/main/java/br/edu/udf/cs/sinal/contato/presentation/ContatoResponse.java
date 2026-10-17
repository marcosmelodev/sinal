package br.edu.udf.cs.sinal.contato.presentation;

import br.edu.udf.cs.sinal.contato.application.ContatoDeConfiancaResumo;

// Telefone em dígitos canônicos (DDD + número); a formatação para exibição é do cliente.
public record ContatoResponse(Long id, String nome, String telefone) {

    static ContatoResponse de(ContatoDeConfiancaResumo contato) {
        return new ContatoResponse(contato.id(), contato.nome(), contato.telefone());
    }

    @Override
    public String toString() {
        return "ContatoResponse[id=" + id + ", nome=" + nome + ", telefone=***]";
    }
}
