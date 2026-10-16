package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncaminhamentoPersistenceMapperTest {

    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");
    private static final Instant T1 = T0.plusSeconds(8);

    private static EncaminhamentoJpaEntity entidade(StatusEncaminhamento status, Instant proxima) {
        return new EncaminhamentoJpaEntity(3L, 10L, ModoEncaminhamento.SIMULADO, status, "JUR-01", "C-PM", T0, proxima);
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Test
    void novaEntidadeDeveCopiarTodosOsCampos() {
        EncaminhamentoJpaEntity entity = EncaminhamentoPersistenceMapper.toNovaEntidade(
                Encaminhamento.agendar(10L, "JUR-01", "C-PM", T0, T1));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getChamadoId()).isEqualTo(10L);
        assertThat(entity.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
        assertThat(entity.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
        assertThat(entity.getJurisdicaoCodigo()).isEqualTo("JUR-01");
        assertThat(entity.getCentralCodigo()).isEqualTo("C-PM");
        assertThat(entity.getDataCriacao()).isEqualTo(T0);
        assertThat(entity.getProximaTransicaoEm()).isEqualTo(T1);
    }

    @Test
    void deveReconstituirEncaminhamentoAgendado() {
        Encaminhamento encaminhamento = EncaminhamentoPersistenceMapper.toDomain(
                entidade(StatusEncaminhamento.AGENDADO, T1));

        assertThat(encaminhamento.getId()).isEqualTo(3L);
        assertThat(encaminhamento.getChamadoId()).isEqualTo(10L);
        assertThat(encaminhamento.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
        assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
        assertThat(encaminhamento.getJurisdicaoCodigo()).isEqualTo("JUR-01");
        assertThat(encaminhamento.getCentralCodigo()).isEqualTo("C-PM");
        assertThat(encaminhamento.getDataCriacao()).isEqualTo(T0);
        assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T1);
    }

    @Test
    void deveReconstituirEncaminhamentoConcluido() {
        Encaminhamento encaminhamento = EncaminhamentoPersistenceMapper.toDomain(
                entidade(StatusEncaminhamento.CONCLUIDO, null));

        assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
        assertThat(encaminhamento.getProximaTransicaoEm()).isNull();
    }

    @Test
    void leituraDeAgendadoSemHorarioDeveViolarEN3() {
        assertCodigo(() -> EncaminhamentoPersistenceMapper.toDomain(entidade(StatusEncaminhamento.AGENDADO, null)),
                "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
    }

    @Test
    void leituraDeConcluidoComHorarioDeveViolarEN3() {
        assertCodigo(() -> EncaminhamentoPersistenceMapper.toDomain(entidade(StatusEncaminhamento.CONCLUIDO, T1)),
                "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
    }
}
