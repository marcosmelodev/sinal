package br.edu.udf.cs.sinal.encaminhamento.application;

// Códigos de referência guardados no Encaminhamento; ambos podem ser nulos (Etapa 5 §11).
public record CentralDeterminada(String jurisdicaoCodigo, String centralCodigo) {

    public static final CentralDeterminada NENHUMA = new CentralDeterminada(null, null);
}
