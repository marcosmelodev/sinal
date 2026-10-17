package br.edu.udf.cs.sinal.chamado.application;

import java.time.Instant;

// Localização capturada no aparelho; validada pelo VO Localizacao (LC1 a LC3) no serviço.
public record LocalizacaoInformada(Double latitude, Double longitude, Float precisao, Instant timestamp) {

    // Coordenadas exatas nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "LocalizacaoInformada[***]";
    }
}
