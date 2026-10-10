package br.edu.udf.cs.sinal.domain.model;

import java.time.Instant;

public class Usuario {

    private Long id;
    private String nome;
    private String cpf;
    private String email;
    private String senhaHash;
    private Instant dataCadastro;
    private Boolean ativo;

    public Usuario(){}

    public Usuario(String nome, String cpf, String email, String senhaHash) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome não pode ser vazio.");
        }
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("CPF é obrigatório.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail inválido..");
        }
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("Senha obrigatória.");
        }

        this.nome = nome.trim();
        this.cpf = normalizarCpf(cpf);
        this.email = email.trim().toLowerCase();
        this.senhaHash = senhaHash;
        this.dataCadastro = Instant.now();
        this.ativo = true;
    }

    public Usuario(Long id, String nome, String cpf, String email, String senhaHash) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.email = email;
        this.senhaHash = senhaHash;
        this.dataCadastro = dataCadastro;
        this.ativo = ativo;
    }

    public static String normalizarCpf(String cpf) {
        return cpf.replaceAll("\\D", "");
    }

    public void desativar(){
        this.ativo = false;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getDataCadastro() {
        return dataCadastro;
    }

    public Boolean getAtivo() {
        return ativo;
    }
}
