package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;

import java.util.List;

final class ChamadoPersistenceMapper {

    private ChamadoPersistenceMapper() {
    }

    // A reconstituição aplica CH1 a CH8 e preserva baixaPrecisao gravado (N2).
    static Chamado toDomain(ChamadoJpaEntity entity) {
        Localizacao localizacao = new Localizacao(entity.getLatitude(), entity.getLongitude(), entity.getPrecisao(),
                entity.getTimestampLocalizacao());
        List<Transicao> transicoes = entity.getTransicoes().stream()
                .map(transicao -> new Transicao(transicao.getEstado(), transicao.getOcorridoEm()))
                .toList();
        return Chamado.reconstituir(entity.getId(), entity.getUsuarioId(), entity.getModalidade(), entity.getOrgao(),
                entity.getOcorrenciaId(), localizacao, entity.getBaixaPrecisao(), entity.getDetalheAdicional(),
                entity.getEstado(), entity.getDataCriacao(), transicoes);
    }

    static ChamadoJpaEntity toNovaEntidade(Chamado chamado, String chaveIdempotencia) {
        Localizacao localizacao = chamado.getLocalizacao();
        return new ChamadoJpaEntity(null, chamado.getUsuarioId(), chamado.getModalidade(), chamado.getOrgao(),
                chamado.getOcorrenciaId(), localizacao.getLatitude(), localizacao.getLongitude(),
                localizacao.getPrecisao(), localizacao.getTimestamp(), chamado.getBaixaPrecisao(),
                chamado.getDetalheAdicional(), chamado.getEstado(), chamado.getDataCriacao(), chaveIdempotencia,
                chamado.getTransicoes().stream().map(ChamadoPersistenceMapper::toNovaTransicao).toList());
    }

    static ChamadoTransicaoJpaEntity toNovaTransicao(Transicao transicao) {
        return new ChamadoTransicaoJpaEntity(null, transicao.getEstado(), transicao.getOcorridoEm());
    }

    // Append-only: as transições gravadas devem ser exatamente o início do histórico do domínio.
    static List<Transicao> novasTransicoes(List<ChamadoTransicaoJpaEntity> gravadas, List<Transicao> doDominio) {
        if (doDominio.size() < gravadas.size()) {
            throw historicoDivergente();
        }
        for (int i = 0; i < gravadas.size(); i++) {
            ChamadoTransicaoJpaEntity gravada = gravadas.get(i);
            Transicao transicao = doDominio.get(i);
            if (gravada.getEstado() != transicao.getEstado()
                    || !gravada.getOcorridoEm().equals(transicao.getOcorridoEm())) {
                throw historicoDivergente();
            }
        }
        return doDominio.subList(gravadas.size(), doDominio.size());
    }

    private static IllegalStateException historicoDivergente() {
        return new IllegalStateException("O histórico de transições gravado diverge do Chamado em memória.");
    }
}
