package br.edu.udf.cs.sinal.insfrastructure.persistence;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;
import jakarta.persistence.*;

@Entity
@Table(name = "tb_ocorrencia_tipo")
public class OcorrenciaTipoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrgaoSeguranca orgao;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private Integer ordem;

    @Column(nullable = false)
    private Boolean ativo;

    protected OcorrenciaTipoEntity(){}

    public OcorrenciaTipo toDomain(){
        return new OcorrenciaTipo(
                this.id,
                this.codigo,
                this.orgao,
                this.nome,
                this.ordem,
                this.ativo
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public OrgaoSeguranca getOrgao() {
        return orgao;
    }

    public void setOrgao(OrgaoSeguranca orgao) {
        this.orgao = orgao;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(Integer ordem) {
        this.ordem = ordem;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}
