package br.edu.udf.cs.sinal.chamado.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransicaoTest {

    private static final Instant INSTANTE = Instant.parse("2026-10-15T12:00:00Z");

    @ParameterizedTest
    @EnumSource(EstadoChamado.class)
    void deveCriarPreservandoEstadoEInstante(EstadoChamado estado) {
        Transicao transicao = new Transicao(estado, INSTANTE);

        assertThat(transicao.getEstado()).isEqualTo(estado);
        assertThat(transicao.getOcorridoEm()).isEqualTo(INSTANTE);
    }

    @Test
    void deveRejeitarEstadoNulo() {
        assertThatThrownBy(() -> new Transicao(null, INSTANTE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarInstanteNulo() {
        assertThatThrownBy(() -> new Transicao(EstadoChamado.CRIADO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveSerIgualQuandoEstadoEInstanteSaoIguais() {
        Transicao a = new Transicao(EstadoChamado.CRIADO, INSTANTE);
        Transicao b = new Transicao(EstadoChamado.CRIADO, INSTANTE);

        assertThat(a).isNotSameAs(b);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void deveSerDiferenteQuandoEstadoOuInstanteDiferem() {
        Transicao base = new Transicao(EstadoChamado.CRIADO, INSTANTE);

        assertThat(base).isNotEqualTo(new Transicao(EstadoChamado.ENCERRADO, INSTANTE));
        assertThat(base).isNotEqualTo(new Transicao(EstadoChamado.CRIADO, INSTANTE.plusMillis(1)));
    }

    @Test
    void deveSerReflexivaENaoIgualANuloOuOutroTipo() {
        Transicao transicao = new Transicao(EstadoChamado.CRIADO, INSTANTE);

        assertThat(transicao).isEqualTo(transicao);
        assertThat(transicao.equals(null)).isFalse();
        assertThat(transicao.equals(EstadoChamado.CRIADO)).isFalse();
    }
}
