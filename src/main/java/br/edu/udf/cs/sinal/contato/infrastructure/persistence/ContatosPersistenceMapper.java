package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;

import java.util.List;

final class ContatosPersistenceMapper {

    private ContatosPersistenceMapper() {
    }

    // A reconstituição aplica as invariantes do domínio também na leitura (CT1 a CT3).
    static ContatosDeConfianca toDomain(Long usuarioId, List<ContatoConfiancaJpaEntity> linhas) {
        List<ContatoDeConfianca> contatos = linhas.stream()
                .map(linha -> ContatoDeConfianca.reconstituir(linha.getId(), linha.getNome(), linha.getTelefone()))
                .toList();
        return ContatosDeConfianca.reconstituir(usuarioId, contatos);
    }

    static ContatoConfiancaJpaEntity toNovaEntidade(Long usuarioId, ContatoDeConfianca contato) {
        return new ContatoConfiancaJpaEntity(null, usuarioId, contato.getNome(), contato.getTelefone());
    }
}
