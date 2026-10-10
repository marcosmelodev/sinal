package br.edu.udf.cs.sinal.presentation.dto.response;

import br.edu.udf.cs.sinal.domain.model.Usuario;

import java.time.Instant;

public record UsuarioResponse(
        Long id,
        String nome,
        String cpfMascarado,
        String email,
        Instant dataCadasstro,
        Boolean ativo
) {

    public static UsuarioResponse fromDomain(Usuario usuario) {
        String cpf = usuario.getCpf();
        String mascarado = (cpf != null && cpf.length() == 11)
                ? "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"
                : "***********";

        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                mascarado,
                usuario.getEmail(),
                usuario.getDataCadastro(),
                usuario.getAtivo()
        );
    }
}
