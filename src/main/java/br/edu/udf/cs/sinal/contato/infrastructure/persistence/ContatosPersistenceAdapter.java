package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Violação de chave estrangeira (usuário inexistente) propaga como DataIntegrityViolationException.
@Repository
public class ContatosPersistenceAdapter implements ContatosRepository {

    private final ContatoConfiancaJpaRepository repository;

    public ContatosPersistenceAdapter(ContatoConfiancaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public ContatosDeConfianca buscarPorUsuario(Long usuarioId) {
        return ContatosPersistenceMapper.toDomain(usuarioId, repository.findByUsuarioIdOrderByIdAsc(usuarioId));
    }

    // MANDATORY: sem transação o bloqueio seria liberado imediatamente, sem proteger o limite.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ContatosDeConfianca buscarPorUsuarioParaAlteracao(Long usuarioId) {
        repository.bloquearUsuario(usuarioId);
        return ContatosPersistenceMapper.toDomain(usuarioId, repository.findByUsuarioIdOrderByIdAsc(usuarioId));
    }

    // Sincronização diferencial: remove os ausentes, atualiza os existentes e insere os novos (id nulo),
    // preservando os ids. Os novos recebem ids maiores, ficando ao fim da lista como no agregado.
    @Override
    @Transactional
    public ContatosDeConfianca salvar(ContatosDeConfianca contatos) {
        Long usuarioId = contatos.getUsuarioId();
        Map<Long, ContatoConfiancaJpaEntity> atuais = repository.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .collect(Collectors.toMap(ContatoConfiancaJpaEntity::getId, Function.identity()));

        Set<Long> mantidos = new HashSet<>();
        List<ContatoConfiancaJpaEntity> novos = new ArrayList<>();
        for (ContatoDeConfianca contato : contatos.getContatos()) {
            if (contato.getId() == null) {
                novos.add(ContatosPersistenceMapper.toNovaEntidade(usuarioId, contato));
                continue;
            }
            ContatoConfiancaJpaEntity linha = atuais.get(contato.getId());
            if (linha == null) {
                throw new IllegalStateException("O contato " + contato.getId()
                        + " não existe mais para o usuário " + usuarioId + ".");
            }
            // O dirty checking só emite UPDATE quando nome ou telefone mudaram.
            linha.atualizar(contato.getNome(), contato.getTelefone());
            mantidos.add(contato.getId());
        }

        repository.deleteAll(atuais.values().stream().filter(linha -> !mantidos.contains(linha.getId())).toList());
        repository.saveAll(novos);
        repository.flush();
        return ContatosPersistenceMapper.toDomain(usuarioId, repository.findByUsuarioIdOrderByIdAsc(usuarioId));
    }
}
