package br.edu.udf.cs.sinal.application.command;

public record CadastrarUsuarioCommand(
        String nome,
        String cpf,
        String email,
        String senha
) {
}
