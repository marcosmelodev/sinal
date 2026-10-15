package br.edu.udf.cs.sinal.compartilhado.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalizacaoTest {

    private static final Double LATITUDE = -15.7939;
    private static final Double LONGITUDE = -47.8828;
    private static final Float PRECISAO = 25.0f;
    private static final Instant TIMESTAMP = Instant.parse("2026-10-15T12:00:00Z");
    private static final Float LIMIAR = 100.0f;

    private static Localizacao comPrecisao(Float precisao) {
        return new Localizacao(LATITUDE, LONGITUDE, precisao, TIMESTAMP);
    }

    private static void assertCodigo(Runnable criacao, String codigo) {
        assertThatThrownBy(criacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Test
    void deveCriarPreservandoOsValores() {
        Localizacao localizacao = new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP);

        assertThat(localizacao.getLatitude()).isEqualTo(LATITUDE);
        assertThat(localizacao.getLongitude()).isEqualTo(LONGITUDE);
        assertThat(localizacao.getPrecisao()).isEqualTo(PRECISAO);
        assertThat(localizacao.getTimestamp()).isEqualTo(TIMESTAMP);
    }

    @Nested
    class LC1Coordenadas {

        static Stream<Double> latitudesValidas() {
            return Stream.of(-90.0, Math.nextUp(-90.0), 0.0, Math.nextDown(90.0), 90.0);
        }

        static Stream<Double> latitudesInvalidas() {
            return Stream.of(Math.nextDown(-90.0), -90.000001, Math.nextUp(90.0), 90.000001, -180.0, 180.0,
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, null);
        }

        static Stream<Double> longitudesValidas() {
            return Stream.of(-180.0, Math.nextUp(-180.0), 0.0, Math.nextDown(180.0), 180.0);
        }

        static Stream<Double> longitudesInvalidas() {
            return Stream.of(Math.nextDown(-180.0), -180.000001, Math.nextUp(180.0), 180.000001,
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, null);
        }

        @ParameterizedTest
        @MethodSource("latitudesValidas")
        void deveAceitarLatitudeDentroDaFaixa(Double latitude) {
            assertThat(new Localizacao(latitude, LONGITUDE, PRECISAO, TIMESTAMP).getLatitude()).isEqualTo(latitude);
        }

        @ParameterizedTest
        @MethodSource("latitudesInvalidas")
        void deveRejeitarLatitudeInvalida(Double latitude) {
            assertCodigo(() -> new Localizacao(latitude, LONGITUDE, PRECISAO, TIMESTAMP),
                    "LOCALIZACAO_LATITUDE_INVALIDA");
        }

        @ParameterizedTest
        @MethodSource("longitudesValidas")
        void deveAceitarLongitudeDentroDaFaixa(Double longitude) {
            assertThat(new Localizacao(LATITUDE, longitude, PRECISAO, TIMESTAMP).getLongitude()).isEqualTo(longitude);
        }

        @ParameterizedTest
        @MethodSource("longitudesInvalidas")
        void deveRejeitarLongitudeInvalida(Double longitude) {
            assertCodigo(() -> new Localizacao(LATITUDE, longitude, PRECISAO, TIMESTAMP),
                    "LOCALIZACAO_LONGITUDE_INVALIDA");
        }
    }

    @Nested
    class LC2Precisao {

        static Stream<Float> precisoesValidas() {
            return Stream.of(0.0f, Float.MIN_VALUE, 100.0f, Float.MAX_VALUE);
        }

        static Stream<Float> precisoesInvalidas() {
            return Stream.of(-0.1f, -Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, null);
        }

        @ParameterizedTest
        @MethodSource("precisoesValidas")
        void deveAceitarPrecisaoFinitaMaiorOuIgualAZero(Float precisao) {
            assertThat(comPrecisao(precisao).getPrecisao()).isEqualTo(precisao);
        }

        @ParameterizedTest
        @MethodSource("precisoesInvalidas")
        void deveRejeitarPrecisaoInvalida(Float precisao) {
            assertCodigo(() -> comPrecisao(precisao), "LOCALIZACAO_PRECISAO_INVALIDA");
        }
    }

    @Nested
    class LC3Timestamp {

        @Test
        void deveRejeitarTimestampNulo() {
            assertCodigo(() -> new Localizacao(LATITUDE, LONGITUDE, PRECISAO, null),
                    "LOCALIZACAO_TIMESTAMP_OBRIGATORIO");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1970-01-01T00:00:00Z", "2000-01-01T00:00:00Z"})
        void naoDeveRejeitarTimestampPelaIdade(String timestamp) {
            Instant antigo = Instant.parse(timestamp);

            assertThat(new Localizacao(LATITUDE, LONGITUDE, PRECISAO, antigo).getTimestamp()).isEqualTo(antigo);
        }
    }

    @Nested
    class LC4BaixaPrecisao {

        static Stream<Arguments> precisoesEResultadoEsperado() {
            return Stream.of(
                    Arguments.of(150.0f, true),
                    Arguments.of(Math.nextUp(100.0f), true),
                    Arguments.of(Float.MAX_VALUE, true),
                    Arguments.of(100.0f, false),
                    Arguments.of(Math.nextDown(100.0f), false),
                    Arguments.of(50.0f, false),
                    Arguments.of(0.0f, false));
        }

        static Stream<Float> limiaresInvalidos() {
            return Stream.of(-1.0f, -Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
        }

        @ParameterizedTest
        @MethodSource("precisoesEResultadoEsperado")
        void deveSerBaixaPrecisaoSomenteQuandoMaiorQueOLimiar(Float precisao, boolean esperado) {
            assertThat(comPrecisao(precisao).ehBaixaPrecisao(LIMIAR)).isEqualTo(esperado);
        }

        @Test
        void deveUsarOLimiarRecebidoSemAlterarALocalizacao() {
            Localizacao localizacao = comPrecisao(PRECISAO);

            assertThat(localizacao.ehBaixaPrecisao(10.0f)).isTrue();
            assertThat(localizacao.ehBaixaPrecisao(LIMIAR)).isFalse();
            assertThat(localizacao.ehBaixaPrecisao(10.0f)).isTrue();
            assertThat(localizacao).isEqualTo(comPrecisao(PRECISAO));
        }

        @Test
        void deveAceitarLimiarZero() {
            assertThat(comPrecisao(0.0f).ehBaixaPrecisao(0.0f)).isFalse();
            assertThat(comPrecisao(Float.MIN_VALUE).ehBaixaPrecisao(0.0f)).isTrue();
        }

        @ParameterizedTest
        @NullSource
        @MethodSource("limiaresInvalidos")
        void deveRejeitarLimiarInvalido(Float limiar) {
            Localizacao localizacao = comPrecisao(PRECISAO);

            assertThatThrownBy(() -> localizacao.ehBaixaPrecisao(limiar))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class LC5IgualdadePorValor {

        static Stream<Localizacao> localizacoesComUmCampoDiferente() {
            return Stream.of(
                    new Localizacao(-15.0, LONGITUDE, PRECISAO, TIMESTAMP),
                    new Localizacao(LATITUDE, -47.0, PRECISAO, TIMESTAMP),
                    new Localizacao(LATITUDE, LONGITUDE, 30.0f, TIMESTAMP),
                    new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP.plusSeconds(1)));
        }

        @Test
        void deveSerIgualQuandoTodosOsValoresSaoIguais() {
            Localizacao a = new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP);
            Localizacao b = new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP);

            assertThat(a).isNotSameAs(b);
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(a);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @ParameterizedTest
        @MethodSource("localizacoesComUmCampoDiferente")
        void deveSerDiferenteQuandoAlgumValorDifere(Localizacao outra) {
            assertThat(new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP)).isNotEqualTo(outra);
        }

        @Test
        void deveSerReflexivaENaoIgualANuloOuOutroTipo() {
            Localizacao localizacao = new Localizacao(LATITUDE, LONGITUDE, PRECISAO, TIMESTAMP);

            assertThat(localizacao).isEqualTo(localizacao);
            assertThat(localizacao.equals(null)).isFalse();
            assertThat(localizacao.equals("localizacao")).isFalse();
        }

        @Test
        void deveNormalizarZeroNegativoParaZero() {
            Localizacao negativo = new Localizacao(-0.0, -0.0, -0.0f, TIMESTAMP);
            Localizacao positivo = new Localizacao(0.0, 0.0, 0.0f, TIMESTAMP);

            assertThat(negativo).isEqualTo(positivo);
            assertThat(negativo.hashCode()).isEqualTo(positivo.hashCode());
            assertThat(Double.doubleToRawLongBits(negativo.getLatitude())).isZero();
            assertThat(Double.doubleToRawLongBits(negativo.getLongitude())).isZero();
            assertThat(Float.floatToRawIntBits(negativo.getPrecisao())).isZero();
        }
    }
}
