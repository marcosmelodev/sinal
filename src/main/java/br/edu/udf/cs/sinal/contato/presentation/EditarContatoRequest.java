package br.edu.udf.cs.sinal.contato.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Validação sintática apenas: o domínio normaliza e valida o telefone brasileiro com DDD (CT3).
public record EditarContatoRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Size(max = 20) String telefone) {

    // Telefone de terceiro nunca aparece em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "EditarContatoRequest[nome=" + nome + ", telefone=***]";
    }
}
