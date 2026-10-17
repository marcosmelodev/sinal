package br.edu.udf.cs.sinal.identidade.application;

// Emissão do access token (Etapa 4 §20); a implementação JWT fica na infraestrutura.
public interface EmissorDeToken {

    TokenEmitido emitir(Long usuarioId);
}
