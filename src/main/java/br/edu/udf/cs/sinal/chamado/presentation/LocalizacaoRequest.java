package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.chamado.application.LocalizacaoInformada;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// Presença apenas: faixas e precisão são validadas pelo VO Localizacao (LC1 a LC3).
public record LocalizacaoRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull Float precisao,
        @NotNull Instant timestamp) {

    LocalizacaoInformada toInformada() {
        return new LocalizacaoInformada(latitude, longitude, precisao, timestamp);
    }

    // Coordenadas exatas nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "LocalizacaoRequest[***]";
    }
}
