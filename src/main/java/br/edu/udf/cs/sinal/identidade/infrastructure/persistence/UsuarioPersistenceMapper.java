package br.edu.udf.cs.sinal.identidade.infrastructure.persistence;

import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;

final class UsuarioPersistenceMapper {

    private UsuarioPersistenceMapper() {
    }

    static UsuarioJpaEntity toJpaEntity(Usuario usuario) {
        return new UsuarioJpaEntity(usuario.getId(), usuario.getNome(), usuario.getCpf(), usuario.getEmail(),
                usuario.getSenhaHash(), usuario.getDataCadastro(), usuario.getAtivo());
    }

    // A reconstituição aplica as invariantes do domínio também na leitura.
    static Usuario toDomain(UsuarioJpaEntity entity) {
        return Usuario.reconstituir(entity.getId(), entity.getNome(), entity.getCpf(), entity.getEmail(),
                entity.getSenhaHash(), entity.getDataCadastro(), entity.getAtivo());
    }
}
