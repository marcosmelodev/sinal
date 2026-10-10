package br.edu.udf.cs.sinal.insfrastructure.persistence;

import br.edu.udf.cs.sinal.domain.model.Usuario;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tb_usuario")
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String senhaHash;

    @Column(nullable = false)
    private Instant dataCadastro;

    @Column(nullable = false)
    private Boolean ativo;

    protected UsuarioEntity(){}

    public static UsuarioEntity fromDomain(Usuario domain){
        UsuarioEntity entity = new UsuarioEntity();
        entity.id = domain.getId();
        entity.nome = domain.getNome();
        entity.cpf = domain.getCpf();
        entity.email = domain.getEmail();
        entity.senhaHash = domain.getSenhaHash();
        entity.dataCadastro = domain.getDataCadastro();
        entity.ativo = domain.getAtivo();
        return entity;
    }

    public Usuario toDomain() {
        return new Usuario(
                this.id,
                this.nome,
                this.cpf,
                this.email,
                this.senhaHash,
                this.dataCadastro,
                this.ativo
        );
    }

    public Long getId() { return id; }
}
