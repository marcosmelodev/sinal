package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

// Histórico append-only: @Immutable impede qualquer UPDATE. A coluna chamado_id é mantida pela associação
// em ChamadoJpaEntity.
@Entity
@Immutable
@Table(name = "chamado_transicao")
public class ChamadoTransicaoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private EstadoChamado estado;

    @Column(nullable = false)
    private Instant ocorridoEm;

    protected ChamadoTransicaoJpaEntity() {
    }

    public ChamadoTransicaoJpaEntity(Long id, EstadoChamado estado, Instant ocorridoEm) {
        this.id = id;
        this.estado = estado;
        this.ocorridoEm = ocorridoEm;
    }

    public Long getId() {
        return id;
    }

    public EstadoChamado getEstado() {
        return estado;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
