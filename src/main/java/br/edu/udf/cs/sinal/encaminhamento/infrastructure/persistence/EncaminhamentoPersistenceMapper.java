package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;

final class EncaminhamentoPersistenceMapper {

    private EncaminhamentoPersistenceMapper() {
    }

    // A reconstituição aplica EN2 e EN3 também na leitura.
    static Encaminhamento toDomain(EncaminhamentoJpaEntity entity) {
        return Encaminhamento.reconstituir(entity.getId(), entity.getChamadoId(), entity.getModo(),
                entity.getStatus(), entity.getJurisdicaoCodigo(), entity.getCentralCodigo(),
                entity.getDataCriacao(), entity.getProximaTransicaoEm());
    }

    static EncaminhamentoJpaEntity toNovaEntidade(Encaminhamento encaminhamento) {
        return new EncaminhamentoJpaEntity(null, encaminhamento.getChamadoId(), encaminhamento.getModo(),
                encaminhamento.getStatus(), encaminhamento.getJurisdicaoCodigo(), encaminhamento.getCentralCodigo(),
                encaminhamento.getDataCriacao(), encaminhamento.getProximaTransicaoEm());
    }
}
