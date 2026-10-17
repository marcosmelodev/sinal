package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// O limite de 500 caracteres do detalhe é aplicado pelo domínio depois de aparar (CH4).
public record RegistrarChamadoRequest(
        @NotNull OrgaoSeguranca orgao,
        @NotNull Long ocorrenciaId,
        String detalheAdicional,
        @NotNull @Valid LocalizacaoRequest localizacao) {

    // O detalhe pode conter dados de saúde: nunca em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "RegistrarChamadoRequest[orgao=" + orgao + ", ocorrenciaId=" + ocorrenciaId + "]";
    }
}
