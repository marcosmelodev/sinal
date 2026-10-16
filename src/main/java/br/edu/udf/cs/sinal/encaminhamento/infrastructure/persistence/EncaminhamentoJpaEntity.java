package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Tabela encaminhamento (V1). Somente status e proximaTransicaoEm são atualizáveis, como no domínio.
// Sem @ManyToOne para chamado: entre agregados guarda-se apenas o id.
@Entity
@Table(name = "encaminhamento")
public class EncaminhamentoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long chamadoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 30)
    private ModoEncaminhamento modo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private StatusEncaminhamento status;

    @Column(updatable = false, length = 100)
    private String jurisdicaoCodigo;

    @Column(updatable = false, length = 100)
    private String centralCodigo;

    @Column(nullable = false, updatable = false)
    private Instant dataCriacao;

    private Instant proximaTransicaoEm;

    protected EncaminhamentoJpaEntity() {
    }

    public EncaminhamentoJpaEntity(Long id, Long chamadoId, ModoEncaminhamento modo, StatusEncaminhamento status,
                                   String jurisdicaoCodigo, String centralCodigo, Instant dataCriacao,
                                   Instant proximaTransicaoEm) {
        this.id = id;
        this.chamadoId = chamadoId;
        this.modo = modo;
        this.status = status;
        this.jurisdicaoCodigo = jurisdicaoCodigo;
        this.centralCodigo = centralCodigo;
        this.dataCriacao = dataCriacao;
        this.proximaTransicaoEm = proximaTransicaoEm;
    }

    // Usado apenas pelo adapter ao persistir reagendar e concluir.
    void atualizarAgendamento(StatusEncaminhamento status, Instant proximaTransicaoEm) {
        this.status = status;
        this.proximaTransicaoEm = proximaTransicaoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getChamadoId() {
        return chamadoId;
    }

    public ModoEncaminhamento getModo() {
        return modo;
    }

    public StatusEncaminhamento getStatus() {
        return status;
    }

    public String getJurisdicaoCodigo() {
        return jurisdicaoCodigo;
    }

    public String getCentralCodigo() {
        return centralCodigo;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getProximaTransicaoEm() {
        return proximaTransicaoEm;
    }
}
