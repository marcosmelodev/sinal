package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.identidade.application.CadastrarUsuarioCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Validação sintática apenas: formato de CPF e e-mail é decidido pelo domínio; a política de senha, pela Application.
// Campos não declarados (id, ativo) são ignorados na desserialização.
public record CadastrarUsuarioRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Size(max = 14) String cpf,
        @NotBlank @Size(max = 254) String email,
        @NotBlank String senha) {

    CadastrarUsuarioCommand toCommand() {
        return new CadastrarUsuarioCommand(nome, cpf, email, senha);
    }

    // Senha e CPF nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "CadastrarUsuarioRequest[nome=" + nome + ", cpf=***, email=" + email + ", senha=***]";
    }
}
