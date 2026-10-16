package br.edu.udf.cs.sinal.identidade.application;

public record CadastrarUsuarioCommand(String nome, String cpf, String email, String senha) {

    // Senha e CPF nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "CadastrarUsuarioCommand[nome=" + nome + ", cpf=***, email=" + email + ", senha=***]";
    }
}
