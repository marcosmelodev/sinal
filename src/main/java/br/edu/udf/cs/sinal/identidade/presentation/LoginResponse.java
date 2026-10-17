package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.identidade.application.TokenEmitido;

import java.time.Instant;

public record LoginResponse(String accessToken, String tipo, Instant expiraEm) {

    static LoginResponse de(TokenEmitido token) {
        return new LoginResponse(token.accessToken(), "Bearer", token.expiraEm());
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=***, tipo=" + tipo + ", expiraEm=" + expiraEm + "]";
    }
}
