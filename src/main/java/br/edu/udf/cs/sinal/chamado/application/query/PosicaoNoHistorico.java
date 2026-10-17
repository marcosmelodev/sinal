package br.edu.udf.cs.sinal.chamado.application.query;

import java.time.Instant;

// Último item visto: a próxima página começa imediatamente depois dele na ordem do Histórico.
public record PosicaoNoHistorico(Instant dataCriacao, Long id) {
}
