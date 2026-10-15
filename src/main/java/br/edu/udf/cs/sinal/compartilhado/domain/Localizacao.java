package br.edu.udf.cs.sinal.compartilhado.domain;

import java.time.Instant;
import java.util.Objects;

public final class Localizacao {

    public static final String LATITUDE_INVALIDA = "LOCALIZACAO_LATITUDE_INVALIDA";
    public static final String LONGITUDE_INVALIDA = "LOCALIZACAO_LONGITUDE_INVALIDA";
    public static final String PRECISAO_INVALIDA = "LOCALIZACAO_PRECISAO_INVALIDA";
    public static final String TIMESTAMP_OBRIGATORIO = "LOCALIZACAO_TIMESTAMP_OBRIGATORIO";

    private final Double latitude;
    private final Double longitude;
    private final Float precisao;
    private final Instant timestamp;

    public Localizacao(Double latitude, Double longitude, Float precisao, Instant timestamp) {
        // LC1: a comparação negada também rejeita NaN; infinitos ficam fora da faixa.
        if (latitude == null || !(latitude >= -90.0 && latitude <= 90.0)) {
            throw new DominioException(LATITUDE_INVALIDA, "A latitude deve estar entre -90 e 90.");
        }
        if (longitude == null || !(longitude >= -180.0 && longitude <= 180.0)) {
            throw new DominioException(LONGITUDE_INVALIDA, "A longitude deve estar entre -180 e 180.");
        }
        // LC2
        if (precisao == null || !Float.isFinite(precisao) || precisao < 0f) {
            throw new DominioException(PRECISAO_INVALIDA, "A precisão deve ser um número finito maior ou igual a zero.");
        }
        // LC3: não há validação de idade do timestamp.
        if (timestamp == null) {
            throw new DominioException(TIMESTAMP_OBRIGATORIO, "O timestamp da localização é obrigatório.");
        }
        // Somar zero converte -0.0 em 0.0, preservando a igualdade por valor (LC5).
        this.latitude = latitude + 0.0;
        this.longitude = longitude + 0.0;
        this.precisao = precisao + 0.0f;
        this.timestamp = timestamp;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    // LC4: o limiar vem da configuração da aplicação; a comparação é estrita.
    public boolean ehBaixaPrecisao(Float limiarMetros) {
        if (limiarMetros == null || !Float.isFinite(limiarMetros) || limiarMetros < 0f) {
            throw new IllegalArgumentException("O limiar de precisão deve ser um número finito maior ou igual a zero.");
        }
        return precisao > limiarMetros;
    }

    // LC5: igualdade por valor. toString não é sobrescrito para não expor coordenadas em logs.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Localizacao outra)) {
            return false;
        }
        return latitude.equals(outra.latitude)
                && longitude.equals(outra.longitude)
                && precisao.equals(outra.precisao)
                && timestamp.equals(outra.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, precisao, timestamp);
    }
}
