package br.edu.udf.cs.sinal.chamado.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Emergência Imediata: somente a localização (Etapa 5, Div. 1). Órgão, ocorrência e detalhe enviados são ignorados.
public record RegistrarEmergenciaImediataRequest(@NotNull @Valid LocalizacaoRequest localizacao) {
}
