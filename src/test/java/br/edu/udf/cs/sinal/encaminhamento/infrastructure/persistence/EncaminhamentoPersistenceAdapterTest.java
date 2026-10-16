package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, EncaminhamentoPersistenceAdapter.class})
class EncaminhamentoPersistenceAdapterTest {

    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");
    private static final Instant T1 = T0.plusSeconds(8);
    private static final Instant T2 = T0.plusSeconds(28);

    @Autowired
    private EncaminhamentoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private Long usuarioId;
    private int sequenciaDeChaves;

    @BeforeEach
    void criarUsuario() {
        entityManager.createNativeQuery("""
                        INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                        VALUES ('Usuário', '52998224725', 'a@exemplo.com', 'hash', TRUE, ?1)""")
                .setParameter(1, Timestamp.from(T0)).executeUpdate();
        usuarioId = ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = '52998224725'")
                .getSingleResult()).longValue();
    }

    private Long inserirChamado() {
        String chave = "chave-" + (++sequenciaDeChaves);
        entityManager.createNativeQuery("""
                        INSERT INTO chamado (usuario_id, modalidade, orgao, ocorrencia_id, latitude, longitude, precisao,
                            timestamp_localizacao, baixa_precisao, detalhe_adicional, estado, data_criacao,
                            chave_idempotencia)
                        VALUES (?1, 'EMERGENCIA_IMEDIATA', 'POLICIA_MILITAR', NULL, -15.79, -47.88, 25.0, ?2, FALSE,
                            NULL, 'CRIADO', ?2, ?3)""")
                .setParameter(1, usuarioId).setParameter(2, Timestamp.from(T0)).setParameter(3, chave)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "SELECT id FROM chamado WHERE usuario_id = ?1 AND chave_idempotencia = ?2")
                .setParameter(1, usuarioId).setParameter(2, chave).getSingleResult()).longValue();
    }

    private Encaminhamento agendar(Long chamadoId, Instant proxima) {
        return adapter.salvar(Encaminhamento.agendar(chamadoId, "JUR-01", "C-PM", T0, proxima));
    }

    // Força a ida ao banco: grava o pendente e descarta o cache de primeiro nível.
    private Encaminhamento recarregar(Long chamadoId) {
        entityManager.flush();
        entityManager.clear();
        return adapter.buscarPorChamadoId(chamadoId).orElseThrow();
    }

    @Test
    void salvarNovoDeveGerarIdEPreservarOsDados() {
        Long chamadoId = inserirChamado();

        Encaminhamento salvo = agendar(chamadoId, T1);

        assertThat(salvo.getId()).isNotNull().isPositive();
        Encaminhamento lido = recarregar(chamadoId);
        assertThat(lido.getId()).isEqualTo(salvo.getId());
        assertThat(lido.getChamadoId()).isEqualTo(chamadoId);
        assertThat(lido.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
        assertThat(lido.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
        assertThat(lido.getJurisdicaoCodigo()).isEqualTo("JUR-01");
        assertThat(lido.getCentralCodigo()).isEqualTo("C-PM");
        assertThat(lido.getDataCriacao()).isEqualTo(T0);
        assertThat(lido.getProximaTransicaoEm()).isEqualTo(T1);
    }

    @Test
    void salvarNovoSemJurisdicaoNemCentralDeveGravarNulos() {
        Long chamadoId = inserirChamado();

        adapter.salvar(Encaminhamento.agendar(chamadoId, null, null, T0, T1));

        Encaminhamento lido = recarregar(chamadoId);
        assertThat(lido.getJurisdicaoCodigo()).isNull();
        assertThat(lido.getCentralCodigo()).isNull();
    }

    @Test
    void reagendarDevePersistirNovoHorarioMantendoAgendado() {
        Long chamadoId = inserirChamado();
        agendar(chamadoId, T1);

        Encaminhamento encaminhamento = recarregar(chamadoId);
        encaminhamento.reagendar(T2);
        adapter.salvar(encaminhamento);

        Encaminhamento lido = recarregar(chamadoId);
        assertThat(lido.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
        assertThat(lido.getProximaTransicaoEm()).isEqualTo(T2);
    }

    @Test
    void concluirDevePersistirConcluidoSemAgendamento() {
        Long chamadoId = inserirChamado();
        agendar(chamadoId, T1);

        Encaminhamento encaminhamento = recarregar(chamadoId);
        encaminhamento.concluir();
        adapter.salvar(encaminhamento);

        Encaminhamento lido = recarregar(chamadoId);
        assertThat(lido.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
        assertThat(lido.getProximaTransicaoEm()).isNull();
    }

    @Test
    void objetoAgendadoDesatualizadoNaoDeveSobrescreverConcluido() {
        Long chamadoId = inserirChamado();
        agendar(chamadoId, T1);
        Encaminhamento desatualizado = recarregar(chamadoId);

        Encaminhamento concluido = recarregar(chamadoId);
        concluido.concluir();
        adapter.salvar(concluido);
        recarregar(chamadoId);

        desatualizado.reagendar(T2);
        assertThatThrownBy(() -> adapter.salvar(desatualizado)).isInstanceOf(IllegalStateException.class);
        assertThat(recarregar(chamadoId).getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
    }

    @Test
    void naoDeveSalvarEncaminhamentoDeOutroChamado() {
        Long chamadoId = inserirChamado();
        Long outroChamado = inserirChamado();
        Encaminhamento salvo = agendar(chamadoId, T1);

        Encaminhamento trocado = Encaminhamento.reconstituir(salvo.getId(), outroChamado, ModoEncaminhamento.SIMULADO,
                StatusEncaminhamento.AGENDADO, "JUR-01", "C-PM", T0, T2);

        assertThatThrownBy(() -> adapter.salvar(trocado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void naoDeveSalvarIdInexistente() {
        Long chamadoId = inserirChamado();
        Encaminhamento fantasma = Encaminhamento.reconstituir(999_999L, chamadoId, ModoEncaminhamento.SIMULADO,
                StatusEncaminhamento.AGENDADO, null, null, T0, T1);

        assertThatThrownBy(() -> adapter.salvar(fantasma)).isInstanceOf(IllegalStateException.class);
    }

    // T2: somente status e proximaTransicaoEm são atualizáveis; demais alterações não chegam ao banco.
    @Test
    void colunasImutaveisNaoDevemSerAtualizadasNoBanco() {
        Long chamadoId = inserirChamado();
        Long id = agendar(chamadoId, T1).getId();
        entityManager.flush();
        entityManager.clear();

        EncaminhamentoJpaEntity entity = entityManager.find(EncaminhamentoJpaEntity.class, id);
        ReflectionTestUtils.setField(entity, "jurisdicaoCodigo", "OUTRA");
        ReflectionTestUtils.setField(entity, "centralCodigo", "OUTRA");
        ReflectionTestUtils.setField(entity, "dataCriacao", T0.minusSeconds(60));

        Encaminhamento lido = recarregar(chamadoId);
        assertThat(lido.getJurisdicaoCodigo()).isEqualTo("JUR-01");
        assertThat(lido.getCentralCodigo()).isEqualTo("C-PM");
        assertThat(lido.getDataCriacao()).isEqualTo(T0);
    }

    @Test
    void buscarPorChamadoIdDeveSerVazioQuandoNaoHaEncaminhamento() {
        Long chamadoId = inserirChamado();

        assertThat(adapter.buscarPorChamadoId(chamadoId)).isEmpty();
        assertThat(adapter.buscarPorChamadoId(999_999L)).isEmpty();
    }

    @Test
    void vencidosDevemRespeitarCorteStatusOrdemELimite() {
        Instant referencia = T0.plusSeconds(100);
        Long noCorte = agendar(inserirChamado(), referencia).getId();
        Long antigo = agendar(inserirChamado(), T0.plusSeconds(10)).getId();
        Long intermediario = agendar(inserirChamado(), T0.plusSeconds(50)).getId();
        agendar(inserirChamado(), referencia.plusMillis(1));
        Long chamadoConcluido = inserirChamado();
        agendar(chamadoConcluido, T0.plusSeconds(5));
        Encaminhamento concluido = recarregar(chamadoConcluido);
        concluido.concluir();
        adapter.salvar(concluido);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.buscarAgendadosVencidos(referencia, 10))
                .extracting(Encaminhamento::getId).containsExactly(antigo, intermediario, noCorte);
        assertThat(adapter.buscarAgendadosVencidos(referencia, 2))
                .extracting(Encaminhamento::getId).containsExactly(antigo, intermediario);
        assertThat(adapter.buscarAgendadosVencidos(T0, 10)).isEmpty();
    }

    @Test
    void vencidosDevemValidarParametros() {
        assertThatThrownBy(() -> adapter.buscarAgendadosVencidos(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.buscarAgendadosVencidos(T0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segundoEncaminhamentoParaOMesmoChamadoDeveViolarEN1() {
        Long chamadoId = inserirChamado();
        agendar(chamadoId, T1);

        assertThatThrownBy(() -> agendar(chamadoId, T2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void encaminhamentoDeChamadoInexistenteDeveViolarChaveEstrangeira() {
        assertThatThrownBy(() -> agendar(999_999L, T1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscarParaAlteracaoDeveFuncionarDentroDeTransacao() {
        Long chamadoId = inserirChamado();
        Long id = agendar(chamadoId, T1).getId();
        Long semEncaminhamento = inserirChamado();
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.buscarPorChamadoIdParaAlteracao(chamadoId))
                .hasValueSatisfying(encaminhamento -> assertThat(encaminhamento.getId()).isEqualTo(id));
        assertThat(adapter.buscarPorChamadoIdParaAlteracao(semEncaminhamento)).isEmpty();
    }
}
