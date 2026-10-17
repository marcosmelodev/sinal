package br.edu.udf.cs.sinal.chamado.application;

import java.time.Instant;

// Evento de aplicação publicado na transação do registro e entregue após o commit (Etapa 5 §17).
// O módulo encaminhamento reage a ele (Etapa 8.5); a reconciliação do job cobre eventos perdidos.
public record ChamadoRegistrado(Long chamadoId, Instant registradoEm) {
}
