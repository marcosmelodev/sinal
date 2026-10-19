package br.edu.udf.cs.sinal.libras.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LibrasChatRequest(@NotBlank @Size(max = 1000) String mensagem) {

    // A mensagem pode conter dados de saúde: nunca aparece em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "LibrasChatRequest[mensagem=***]";
    }
}
