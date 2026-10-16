package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Uma linha por contato; o agregado ContatosDeConfianca é o conjunto das linhas de um usuario_id.
// Sem @ManyToOne: entre módulos o JPA guarda apenas ids (Etapa 4 §12).
@Entity
@Table(name = "contato_confianca")
public class ContatoConfiancaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(nullable = false, length = 11)
    private String telefone;

    protected ContatoConfiancaJpaEntity() {
    }

    public ContatoConfiancaJpaEntity(Long id, Long usuarioId, String nome, String telefone) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.telefone = telefone;
    }

    // Usado apenas pelo adapter na sincronização de contatos editados.
    void atualizar(String nome, String telefone) {
        this.nome = nome;
        this.telefone = telefone;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }
}
