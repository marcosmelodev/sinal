package br.edu.udf.cs.sinal.jurisdicao.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.util.Objects;

// Value Object montado pelo adapter de jurisdição; dados inválidos indicam falha técnica.
public final class CentralAtendimento {

    private final String codigo;
    private final String nome;
    private final OrgaoSeguranca orgao;

    public CentralAtendimento(String codigo, String nome, OrgaoSeguranca orgao) {
        this.codigo = textoObrigatorio(codigo, "O código da central é obrigatório.");
        this.nome = textoObrigatorio(nome, "O nome da central é obrigatório.");
        if (orgao == null) {
            throw new IllegalArgumentException("O órgão da central é obrigatório.");
        }
        this.orgao = orgao;
    }

    private static String textoObrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor.trim();
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public OrgaoSeguranca getOrgao() {
        return orgao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CentralAtendimento outra)) {
            return false;
        }
        return codigo.equals(outra.codigo) && nome.equals(outra.nome) && orgao == outra.orgao;
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo, nome, orgao);
    }
}
