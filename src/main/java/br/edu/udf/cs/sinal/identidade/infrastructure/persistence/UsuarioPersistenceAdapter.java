package br.edu.udf.cs.sinal.identidade.infrastructure.persistence;

import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Violações de unicidade (CPF, e-mail) propagam como DataIntegrityViolationException; a Application as traduz.
@Repository
public class UsuarioPersistenceAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository repository;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioJpaEntity salvo = repository.save(UsuarioPersistenceMapper.toJpaEntity(usuario));
        return UsuarioPersistenceMapper.toDomain(salvo);
    }

    @Override
    public Optional<Usuario> buscarPorCpf(String cpf) {
        return repository.findByCpf(cpf).map(UsuarioPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(email).map(UsuarioPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id).map(UsuarioPersistenceMapper::toDomain);
    }
}
