package br.edu.udf.cs.sinal.insfrastructure.persistence.adapter;

import br.edu.udf.cs.sinal.domain.model.Usuario;
import br.edu.udf.cs.sinal.domain.repositories.UsuarioRepository;
import br.edu.udf.cs.sinal.insfrastructure.persistence.UsuarioEntity;
import br.edu.udf.cs.sinal.insfrastructure.persistence.repository.SpringDataUsuarioRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepository {
    private final SpringDataUsuarioRepository repository;

    public UsuarioRepositoryAdapter(SpringDataUsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioEntity entity = UsuarioEntity.fromDomain(usuario);
        UsuarioEntity salvo = repository.save(entity);
        return salvo.toDomain();
    }

    @Override
    public Optional<Usuario> buscarPorCpf(String cpf) {
        return repository.findByCpf(cpf).map(UsuarioEntity::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(email).map(UsuarioEntity::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return repository.findById(id).map(UsuarioEntity::toDomain);
    }
}
