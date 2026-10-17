package br.edu.udf.cs.sinal.identidade.presentation;

// Sem Bean Validation de propósito: campos ausentes respondem como credenciais inválidas (401), sem um caminho
// de resposta diferente que revele algo sobre o identificador.
public record LoginRequest(String identificador, String senha) {

    // Identificador (CPF ou e-mail) e senha nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "LoginRequest[identificador=***, senha=***]";
    }
}
