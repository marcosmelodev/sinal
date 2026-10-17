package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;

// Telefone em dígitos canônicos (DDD + número); a formatação para exibição é do cliente.
public record ContatoDeConfiancaResumo(Long id, String nome, String telefone) {

    static ContatoDeConfiancaResumo de(ContatoDeConfianca contato) {
        return new ContatoDeConfiancaResumo(contato.getId(), contato.getNome(), contato.getTelefone());
    }

    // Telefone de terceiro nunca aparece em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "ContatoDeConfiancaResumo[id=" + id + ", nome=" + nome + ", telefone=***]";
    }
}
