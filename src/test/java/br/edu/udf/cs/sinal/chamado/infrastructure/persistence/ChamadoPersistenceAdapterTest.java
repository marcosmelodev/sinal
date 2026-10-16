package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ChamadoPersistenceAdapter.class})
class ChamadoPersistenceAdapterTest {

    private static final Float LIMIAR = 100.0f;
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00.123456Z");
    private static final Instant T1 = T0.plusSeconds(8);
    private static final Instant T2 = T0.plusSeconds(28);
    private static final Localizacao LOCALIZACAO = new Localizacao(-15.7939, -47.8828, 25.5f, T0.minusSeconds(2));

    @Autowired
    private ChamadoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private Long usuarioA;
    private Long usuarioB;
    private Long ocorrenciaSamu;

    @BeforeEach
    void prepararDados() {
        usuarioA = inserirUsuario("52998224725", "a@exemplo.com");
        usuarioB = inserirUsuario("11144477735", "b@exemplo.com");
        ocorrenciaSamu = ((Number) entityManager
                .createNativeQuery("SELECT id FROM ocorrencia_tipo WHERE codigo = 'SAMU_OUTRO'")
                .getSingleResult()).longValue();
    }

    private Long inserirUsuario(String cpf, String email) {
        entityManager.createNativeQuery("""
                        INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                        VALUES ('Usuário', ?1, ?2, 'hash', TRUE, ?3)""")
                .setParameter(1, cpf).setParameter(2, email).setParameter(3, Timestamp.from(T0))
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = ?1")
                .setParameter(1, cpf).getSingleResult()).longValue();
    }

    private Chamado padrao(Long usuarioId, Instant instante) {
        return Chamado.registrar(usuarioId, OrgaoSeguranca.SAMU, ocorrenciaSamu, LOCALIZACAO, "  Detalhe  ",
                LIMIAR, instante);
    }

    // Força a ida ao banco: grava o pendente e descarta o cache de primeiro nível.
    private void sincronizarComOBanco() {
        entityManager.flush();
        entityManager.clear();
    }

    private Chamado recarregar(Long id) {
        sincronizarComOBanco();
        return adapter.buscarPorId(id).orElseThrow();
    }

    private long linhasDeTransicao(Long chamadoId) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM chamado_transicao WHERE chamado_id = ?1")
                .setParameter(1, chamadoId).getSingleResult()).longValue();
    }

    @Test
    void salvarNovoPadraoDeveGerarIdChaveETransicaoInicial() {
        Chamado salvo = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1");

        assertThat(salvo.getId()).isNotNull().isPositive();
        Chamado lido = recarregar(salvo.getId());
        assertThat(lido.getUsuarioId()).isEqualTo(usuarioA);
        assertThat(lido.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
        assertThat(lido.getOrgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(lido.getOcorrenciaId()).isEqualTo(ocorrenciaSamu);
        assertThat(lido.getLocalizacao()).isEqualTo(LOCALIZACAO);
        assertThat(lido.getBaixaPrecisao()).isFalse();
        assertThat(lido.getDetalheAdicional()).isEqualTo("Detalhe");
        assertThat(lido.getEstado()).isEqualTo(EstadoChamado.CRIADO);
        assertThat(lido.getDataCriacao()).isEqualTo(T0);
        assertThat(lido.getTransicoes()).containsExactly(new Transicao(EstadoChamado.CRIADO, T0));
        assertThat(linhasDeTransicao(salvo.getId())).isEqualTo(1);
        assertThat(entityManager.createNativeQuery("SELECT chave_idempotencia FROM chamado WHERE id = ?1")
                .setParameter(1, salvo.getId()).getSingleResult()).isEqualTo("chave-1");
    }

    @Test
    void salvarNovoEmergenciaImediataDevePersistirPmSemOcorrenciaNemDetalhe() {
        Localizacao imprecisa = new Localizacao(-15.7939, -47.8828, 150.0f, T0);
        Chamado salvo = adapter.salvarNovo(
                Chamado.registrarEmergenciaImediata(usuarioA, imprecisa, LIMIAR, T0), "chave-ei");

        Chamado lido = recarregar(salvo.getId());
        assertThat(lido.getModalidade()).isEqualTo(ModalidadeChamado.EMERGENCIA_IMEDIATA);
        assertThat(lido.getOrgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
        assertThat(lido.getOcorrenciaId()).isNull();
        assertThat(lido.getDetalheAdicional()).isNull();
        assertThat(lido.getBaixaPrecisao()).isTrue();
    }

    @Test
    void buscarPorUsuarioEChaveDeveRespeitarOUsuario() {
        Chamado salvo = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1");
        sincronizarComOBanco();

        assertThat(adapter.buscarPorUsuarioEChaveIdempotencia(usuarioA, "chave-1"))
                .hasValueSatisfying(lido -> assertThat(lido.getId()).isEqualTo(salvo.getId()));
        assertThat(adapter.buscarPorUsuarioEChaveIdempotencia(usuarioB, "chave-1")).isEmpty();
        assertThat(adapter.buscarPorUsuarioEChaveIdempotencia(usuarioA, "outra")).isEmpty();
    }

    @Test
    void buscarPorIdInexistenteDeveSerVazio() {
        assertThat(adapter.buscarPorId(999_999L)).isEmpty();
    }

    @Test
    void transicoesSucessivasDevemAcrescentarLinhasEAtualizarOEstado() {
        Long id = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1").getId();
        Long idDaPrimeiraLinha = ((Number) entityManager
                .createNativeQuery("SELECT id FROM chamado_transicao WHERE chamado_id = ?1")
                .setParameter(1, id).getSingleResult()).longValue();

        Chamado chamado = recarregar(id);
        chamado.encaminharSimulado(T1);
        adapter.salvar(chamado);
        Chamado encaminhado = recarregar(id);
        assertThat(encaminhado.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
        assertThat(linhasDeTransicao(id)).isEqualTo(2);

        encaminhado.encerrar(T2);
        adapter.salvar(encaminhado);
        Chamado encerrado = recarregar(id);
        assertThat(encerrado.getEstado()).isEqualTo(EstadoChamado.ENCERRADO);
        assertThat(encerrado.getTransicoes()).containsExactly(new Transicao(EstadoChamado.CRIADO, T0),
                new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T1), new Transicao(EstadoChamado.ENCERRADO, T2));
        assertThat(entityManager.createNativeQuery(
                        "SELECT id FROM chamado_transicao WHERE chamado_id = ?1 ORDER BY ocorrido_em, id")
                .setParameter(1, id).getResultList().get(0))
                .isEqualTo(idDaPrimeiraLinha);
    }

    @Test
    void salvarSemMudancasNaoDeveAcrescentarLinhas() {
        Long id = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1").getId();

        adapter.salvar(recarregar(id));
        sincronizarComOBanco();

        assertThat(linhasDeTransicao(id)).isEqualTo(1);
    }

    @Test
    void salvarComHistoricoDivergenteDeveSerRejeitado() {
        Long id = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1").getId();
        Chamado emMemoria = recarregar(id);
        emMemoria.encaminharSimulado(T1);

        // Outra transação avançou o Chamado com outro instante.
        Chamado concorrente = recarregar(id);
        concorrente.encaminharSimulado(T1.plusSeconds(1));
        adapter.salvar(concorrente);
        sincronizarComOBanco();

        assertThatThrownBy(() -> adapter.salvar(emMemoria)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void salvarChamadoInexistenteDeveSerRejeitado() {
        Chamado fantasma = Chamado.reconstituir(999_999L, usuarioA, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                ocorrenciaSamu, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0,
                List.of(new Transicao(EstadoChamado.CRIADO, T0)));

        assertThatThrownBy(() -> adapter.salvar(fantasma)).isInstanceOf(IllegalStateException.class);
    }

    // CH9: as colunas fundamentais são updatable = false; uma alteração indevida na entidade não chega ao banco.
    @Test
    void dadosFundamentaisNaoDevemSerAtualizadosNoBanco() {
        Long id = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1").getId();
        sincronizarComOBanco();

        ChamadoJpaEntity entity = entityManager.find(ChamadoJpaEntity.class, id);
        ReflectionTestUtils.setField(entity, "detalheAdicional", "Alterado");
        ReflectionTestUtils.setField(entity, "orgao", OrgaoSeguranca.POLICIA_MILITAR);
        ReflectionTestUtils.setField(entity, "latitude", 10.0);
        ReflectionTestUtils.setField(entity, "chaveIdempotencia", "outra");

        Chamado lido = recarregar(id);
        assertThat(lido.getDetalheAdicional()).isEqualTo("Detalhe");
        assertThat(lido.getOrgao()).isEqualTo(OrgaoSeguranca.SAMU);
        assertThat(lido.getLocalizacao()).isEqualTo(LOCALIZACAO);
        assertThat(adapter.buscarPorUsuarioEChaveIdempotencia(usuarioA, "chave-1")).isPresent();
    }

    @Test
    void chaveRepetidaParaOMesmoUsuarioDeveViolarUnicidade() {
        adapter.salvarNovo(padrao(usuarioA, T0), "chave-1");

        assertThatThrownBy(() -> adapter.salvarNovo(padrao(usuarioA, T1), "chave-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mesmaChaveParaUsuariosDiferentesDeveSerAceita() {
        adapter.salvarNovo(padrao(usuarioA, T0), "chave-1");
        adapter.salvarNovo(padrao(usuarioB, T0), "chave-1");
        sincronizarComOBanco();

        assertThat(adapter.buscarPorUsuarioEChaveIdempotencia(usuarioB, "chave-1")).isPresent();
    }

    @Test
    void deveRejeitarChaveAusente() {
        assertThatThrownBy(() -> adapter.salvarNovo(padrao(usuarioA, T0), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.salvarNovo(padrao(usuarioA, T0), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarOperacaoIncompativelComOId() {
        Chamado salvo = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1");

        assertThatThrownBy(() -> adapter.salvarNovo(salvo, "chave-2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.salvar(padrao(usuarioA, T0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconciliacaoDeveDevolverSomenteCriadosSemEncaminhamentoAntesDoCorte() {
        Long antigo = adapter.salvarNovo(padrao(usuarioA, T0), "c1").getId();
        Long maisNovo = adapter.salvarNovo(padrao(usuarioA, T0.plusSeconds(60)), "c2").getId();
        Long comEncaminhamento = adapter.salvarNovo(padrao(usuarioA, T0.plusSeconds(10)), "c3").getId();
        Long encaminhado = adapter.salvarNovo(padrao(usuarioA, T0.plusSeconds(20)), "c4").getId();
        Long depoisDoCorte = adapter.salvarNovo(padrao(usuarioA, T0.plusSeconds(600)), "c5").getId();
        sincronizarComOBanco();

        entityManager.createNativeQuery("""
                        INSERT INTO encaminhamento (chamado_id, modo, status, data_criacao, proxima_transicao_em)
                        VALUES (?1, 'SIMULADO', 'AGENDADO', ?2, ?2)""")
                .setParameter(1, comEncaminhamento).setParameter(2, Timestamp.from(T0)).executeUpdate();
        Chamado avancado = adapter.buscarPorId(encaminhado).orElseThrow();
        avancado.encaminharSimulado(T0.plusSeconds(30));
        adapter.salvar(avancado);
        sincronizarComOBanco();

        Instant corte = T0.plusSeconds(300);
        assertThat(adapter.buscarCriadosSemEncaminhamento(corte, 10))
                .extracting(Chamado::getId).containsExactly(antigo, maisNovo);
        assertThat(adapter.buscarCriadosSemEncaminhamento(corte, 1))
                .extracting(Chamado::getId).containsExactly(antigo);
        assertThat(adapter.buscarCriadosSemEncaminhamento(T0, 10)).isEmpty();
        assertThat(depoisDoCorte).isNotNull();
    }

    @Test
    void reconciliacaoDeveValidarParametros() {
        assertThatThrownBy(() -> adapter.buscarCriadosSemEncaminhamento(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.buscarCriadosSemEncaminhamento(T0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buscarParaAlteracaoDeveDevolverOChamadoDentroDeTransacao() {
        Long id = adapter.salvarNovo(padrao(usuarioA, T0), "chave-1").getId();
        sincronizarComOBanco();

        assertThat(adapter.buscarPorIdParaAlteracao(id))
                .hasValueSatisfying(chamado -> assertThat(chamado.getId()).isEqualTo(id));
        assertThat(adapter.buscarPorIdParaAlteracao(999_999L)).isEmpty();
    }
}
