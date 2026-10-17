package br.edu.udf.cs.sinal.identidade.application;

import java.time.Instant;

public record TokenEmitido(String accessToken, Instant expiraEm) {

    // O token é uma credencial: nunca em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "TokenEmitido[accessToken=***, expiraEm=" + expiraEm + "]";
    }
}
