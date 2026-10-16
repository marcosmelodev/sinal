package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ChamadoPersistenceMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");
    private static final Instant T1 = T0.plusSeconds(8);
    private static final Instant T2 = T0.plusSeconds(28);
    private static final Localizacao LOCALIZACAO = new Localizacao(-15.7939, -47.8828, 150.5f, T0.minusSeconds(3));

    private static Chamado padraoEncaminhado() {
        Chamado chamado = Chamado.registrar(1L, OrgaoSeguranca.SAMU, 10L, LOCALIZACAO, " Detalhe ", 100.0f, T0);
        chamado.encaminharSimulado(T1);
        return chamado;
    }

    private static ChamadoJpaEntity entidade(EstadoChamado estado, List<ChamadoTransicaoJpaEntity> transicoes) {
        return new ChamadoJpaEntity(5L, 1L, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU, 10L, -15.7939, -47.8828,
                150.5f, T0.minusSeconds(3), true, "Detalhe", estado, T0, "chave-1", transicoes);
    }

    private static ChamadoTransicaoJpaEntity gravada(long id, EstadoChamado estado, Instant instante) {
        return new ChamadoTransicaoJpaEntity(id, estado, instante);
    }

    @Test
    void novaEntidadeDeveCopiarTodosOsCamposEAChave() {
        ChamadoJpaEntity entity = ChamadoPersistenceMapper.toNovaEntidade(padraoEncaminhado(), "chave-1");

        assertThat(entity.getId()).isNull();
        assertThat(entity.getUsuarioId()).isEqualTo(1L);
        assertThat(entity.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
        assertThat(entity.getOrgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(entity.getOcorrenciaId()).isEqualTo(10L);
        assertThat(entity.getLatitude()).isEqualTo(-15.7939);
        assertThat(entity.getLongitude()).isEqualTo(-47.8828);
        assertThat(entity.getPrecisao()).isEqualTo(150.5f);
        assertThat(entity.getTimestampLocalizacao()).isEqualTo(T0.minusSeconds(3));
        assertThat(entity.getBaixaPrecisao()).isTrue();
        assertThat(entity.getDetalheAdicional()).isEqualTo("Detalhe");
        assertThat(entity.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
        assertThat(entity.getDataCriacao()).isEqualTo(T0);
        assertThat(entity.getChaveIdempotencia()).isEqualTo("chave-1");
        assertThat(entity.getTransicoes())
                .extracting(ChamadoTransicaoJpaEntity::getId, ChamadoTransicaoJpaEntity::getEstado,
                        ChamadoTransicaoJpaEntity::getOcorridoEm)
                .containsExactly(tuple(null, EstadoChamado.CRIADO, T0),
                        tuple(null, EstadoChamado.ENCAMINHADO_SIMULADO, T1));
    }

    @Test
    void toDomainDeveReconstituirTodosOsCamposELocalizacao() {
        Chamado chamado = ChamadoPersistenceMapper.toDomain(entidade(EstadoChamado.ENCAMINHADO_SIMULADO, List.of(
                gravada(1, EstadoChamado.CRIADO, T0), gravada(2, EstadoChamado.ENCAMINHADO_SIMULADO, T1))));

        assertThat(chamado.getId()).isEqualTo(5L);
        assertThat(chamado.getUsuarioId()).isEqualTo(1L);
        assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
        assertThat(chamado.getOrgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(chamado.getOcorrenciaId()).isEqualTo(10L);
        assertThat(chamado.getLocalizacao()).isEqualTo(LOCALIZACAO);
        assertThat(chamado.getBaixaPrecisao()).isTrue();
        assertThat(chamado.getDetalheAdicional()).isEqualTo("Detalhe");
        assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
        assertThat(chamado.getDataCriacao()).isEqualTo(T0);
        assertThat(chamado.getTransicoes()).containsExactly(
                new Transicao(EstadoChamado.CRIADO, T0), new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T1));
    }

    @Test
    void idaEVoltaDevePreservarOChamado() {
        Chamado original = padraoEncaminhado();

        Chamado reconstituido = ChamadoPersistenceMapper.toDomain(new ChamadoJpaEntity(9L, 1L,
                original.getModalidade(), original.getOrgao(), original.getOcorrenciaId(),
                original.getLocalizacao().getLatitude(), original.getLocalizacao().getLongitude(),
                original.getLocalizacao().getPrecisao(), original.getLocalizacao().getTimestamp(),
                original.getBaixaPrecisao(), original.getDetalheAdicional(), original.getEstado(),
                original.getDataCriacao(), "chave-1",
                ChamadoPersistenceMapper.toNovaEntidade(original, "chave-1").getTransicoes()));

        assertThat(reconstituido.getLocalizacao()).isEqualTo(original.getLocalizacao());
        assertThat(reconstituido.getTransicoes()).isEqualTo(original.getTransicoes());
        assertThat(reconstituido.getEstado()).isEqualTo(original.getEstado());
    }

    @Test
    void leituraDeHistoricoInconsistenteDeveViolarInvariante() {
        ChamadoJpaEntity entity = entidade(EstadoChamado.ENCERRADO, List.of(
                gravada(1, EstadoChamado.CRIADO, T0), gravada(2, EstadoChamado.ENCERRADO, T2)));

        assertThatThrownBy(() -> ChamadoPersistenceMapper.toDomain(entity))
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("CHAMADO_TRANSICAO_INVALIDA");
    }

    @Test
    void novasTransicoesDeveDevolverSomenteAsNaoGravadas() {
        List<Transicao> doDominio = List.of(new Transicao(EstadoChamado.CRIADO, T0),
                new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T1), new Transicao(EstadoChamado.ENCERRADO, T2));

        assertThat(ChamadoPersistenceMapper.novasTransicoes(
                List.of(gravada(1, EstadoChamado.CRIADO, T0)), doDominio))
                .containsExactly(doDominio.get(1), doDominio.get(2));
        assertThat(ChamadoPersistenceMapper.novasTransicoes(List.of(gravada(1, EstadoChamado.CRIADO, T0),
                gravada(2, EstadoChamado.ENCAMINHADO_SIMULADO, T1), gravada(3, EstadoChamado.ENCERRADO, T2)),
                doDominio)).isEmpty();
    }

    @Test
    void novasTransicoesDeveRejeitarHistoricoDivergente() {
        List<Transicao> doDominio = List.of(new Transicao(EstadoChamado.CRIADO, T0));

        assertThatThrownBy(() -> ChamadoPersistenceMapper.novasTransicoes(
                List.of(gravada(1, EstadoChamado.CRIADO, T1)), doDominio))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ChamadoPersistenceMapper.novasTransicoes(List.of(gravada(1, EstadoChamado.CRIADO, T0),
                gravada(2, EstadoChamado.ENCAMINHADO_SIMULADO, T1)), doDominio))
                .isInstanceOf(IllegalStateException.class);
    }
}
