package br.edu.udf.cs.sinal.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

public class OcorrenciaTipo {

    private Long id;
    private String codigo;
    private OrgaoSeguranca orgao;
    private String nome;
    private Integer ordem;
    private Boolean ativo;

    public OcorrenciaTipo(Long id, String codigo, OrgaoSeguranca orgao, String nome, Integer ordem, Boolean ativo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código da ocorrência é obrigatório.");
        }
        if (orgao == null) {
            throw new IllegalArgumentException("Órgão de segurança é obrigatório.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da ocorrência é obrigatório.");
        }
        this.id = id;
        this.codigo = codigo.trim().toUpperCase();
        this.orgao = orgao;
        this.nome = nome.trim();
        this.ordem = ordem != null ? ordem : 0;
        this.ativo = ativo != null ? ativo : true;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public OrgaoSeguranca getOrgao() { return orgao; }
    public String getNome() { return nome; }
    public Integer getOrdem() { return ordem; }
    public Boolean getAtivo() { return ativo; }
}
