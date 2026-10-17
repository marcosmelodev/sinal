package br.edu.udf.cs.sinal.plataforma.web;

// Item de errors[] no problem+json (Etapa 4 §16); field é nulo quando o campo não é identificável.
public record ErroDeCampo(String field, String code, String message) {
}
