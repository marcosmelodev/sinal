package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Tabela chamado (V1), com a Localizacao em colunas achatadas (Etapa 4 §12).
// CH9: os dados fundamentais são updatable = false; somente estado é atualizado.
// Sem @ManyToOne para usuario e ocorrencia_tipo: entre agregados guarda-se apenas o id.
@Entity
@Table(name = "chamado")
public class ChamadoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private ModalidadeChamado modalidade;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private OrgaoSeguranca orgao;

    @Column(updatable = false)
    private Long ocorrenciaId;

    @Column(nullable = false, updatable = false)
    private Double latitude;

    @Column(nullable = false, updatable = false)
    private Double longitude;

    @Column(nullable = false, updatable = false)
    private Float precisao;

    @Column(nullable = false, updatable = false)
    private Instant timestampLocalizacao;

    @Column(nullable = false, updatable = false)
    private Boolean baixaPrecisao;

    @Column(updatable = false, length = 500)
    private String detalheAdicional;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EstadoChamado estado;

    @Column(nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(nullable = false, updatable = false, length = 100)
    private String chaveIdempotencia;

    // Mesmo agregado: a cascata grava as transições novas. Sem REMOVE nem orphanRemoval: o histórico só cresce.
    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "chamado_id", nullable = false, updatable = false)
    @OrderBy("ocorridoEm ASC, id ASC")
    private List<ChamadoTransicaoJpaEntity> transicoes = new ArrayList<>();

    protected ChamadoJpaEntity() {
    }

    public ChamadoJpaEntity(Long id, Long usuarioId, ModalidadeChamado modalidade, OrgaoSeguranca orgao,
                            Long ocorrenciaId, Double latitude, Double longitude, Float precisao,
                            Instant timestampLocalizacao, Boolean baixaPrecisao, String detalheAdicional,
                            EstadoChamado estado, Instant dataCriacao, String chaveIdempotencia,
                            List<ChamadoTransicaoJpaEntity> transicoes) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.modalidade = modalidade;
        this.orgao = orgao;
        this.ocorrenciaId = ocorrenciaId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.precisao = precisao;
        this.timestampLocalizacao = timestampLocalizacao;
        this.baixaPrecisao = baixaPrecisao;
        this.detalheAdicional = detalheAdicional;
        this.estado = estado;
        this.dataCriacao = dataCriacao;
        this.chaveIdempotencia = chaveIdempotencia;
        this.transicoes.addAll(transicoes);
    }

    // Usados apenas pelo adapter ao persistir transições da máquina de estados.
    void atualizarEstado(EstadoChamado estado) {
        this.estado = estado;
    }

    void acrescentarTransicao(ChamadoTransicaoJpaEntity transicao) {
        this.transicoes.add(transicao);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public ModalidadeChamado getModalidade() {
        return modalidade;
    }

    public OrgaoSeguranca getOrgao() {
        return orgao;
    }

    public Long getOcorrenciaId() {
        return ocorrenciaId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Float getPrecisao() {
        return precisao;
    }

    public Instant getTimestampLocalizacao() {
        return timestampLocalizacao;
    }

    public Boolean getBaixaPrecisao() {
        return baixaPrecisao;
    }

    public String getDetalheAdicional() {
        return detalheAdicional;
    }

    public EstadoChamado getEstado() {
        return estado;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public List<ChamadoTransicaoJpaEntity> getTransicoes() {
        return Collections.unmodifiableList(transicoes);
    }
}
