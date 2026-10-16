package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;

import java.time.Instant;

// Resultado do cadastro: sem o hash da senha e com o CPF mascarado (Etapa 4 §23).
public record UsuarioCadastrado(Long id, String nome, String cpfMascarado, String email, Instant dataCadastro,
                                Boolean ativo) {

    static UsuarioCadastrado de(Usuario usuario) {
        return new UsuarioCadastrado(usuario.getId(), usuario.getNome(), mascarar(usuario.getCpf()),
                usuario.getEmail(), usuario.getDataCadastro(), usuario.getAtivo());
    }

    // O CPF do domínio é sempre canônico (11 dígitos): ***.982.247-**
    private static String mascarar(String cpf) {
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }
}
