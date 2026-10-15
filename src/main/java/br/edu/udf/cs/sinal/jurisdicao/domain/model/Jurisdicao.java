package br.edu.udf.cs.sinal.jurisdicao.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Value Object montado pelo adapter de jurisdição; dados inválidos indicam falha técnica.
public final class Jurisdicao {

    private final String codigo;
    private final String nome;
    private final List<CentralAtendimento> centrais;

    public Jurisdicao(String codigo, String nome, List<CentralAtendimento> centrais) {
        this.codigo = textoObrigatorio(codigo, "O código da jurisdição é obrigatório.");
        this.nome = textoObrigatorio(nome, "O nome da jurisdição é obrigatório.");
        this.centrais = validarCentrais(centrais);
    }

    // Devolve a central do órgão informado, se houver.
    public Optional<CentralAtendimento> centralPara(OrgaoSeguranca orgao) {
        if (orgao == null) {
            throw new IllegalArgumentException("O órgão é obrigatório.");
        }
        return centrais.stream().filter(central -> central.getOrgao() == orgao).findFirst();
    }

    // Uma central por órgão, para que centralPara não seja ambíguo.
    private static List<CentralAtendimento> validarCentrais(List<CentralAtendimento> centrais) {
        if (centrais == null) {
            throw new IllegalArgumentException("A lista de centrais é obrigatória.");
        }
        Set<OrgaoSeguranca> orgaos = EnumSet.noneOf(OrgaoSeguranca.class);
        for (CentralAtendimento central : centrais) {
            if (central == null) {
                throw new IllegalArgumentException("A lista de centrais não pode conter nulos.");
            }
            if (!orgaos.add(central.getOrgao())) {
                throw new IllegalArgumentException("A jurisdição possui mais de uma central para o órgão "
                        + central.getOrgao() + ".");
            }
        }
        return List.copyOf(centrais);
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

    public List<CentralAtendimento> getCentrais() {
        return centrais;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Jurisdicao outra)) {
            return false;
        }
        return codigo.equals(outra.codigo) && nome.equals(outra.nome) && centrais.equals(outra.centrais);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo, nome, centrais);
    }
}
