package br.edu.udf.cs.sinal.encaminhamento.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Transações reais e concorrentes: a classe não é transacional, grava dados de fato e os remove ao final.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, EncaminhamentoPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EncaminhamentoConcorrenciaTest {

    private static final String CPF = "10000000108";
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");
    private static final long TEMPO_MAXIMO_SEGUNDOS = 10;

    @Autowired
    private EncaminhamentoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transacao;
    private ExecutorService executor;
    private Long usuarioId;
    private Long chamadoId;

    @BeforeEach
    void prepararEncaminhamentoAgendado() {
        transacao = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        transacao.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                            VALUES ('Usuário', ?1, 'concorrencia-encaminhamento@exemplo.com', 'hash', TRUE, ?2)""")
                    .setParameter(1, CPF).setParameter(2, Timestamp.from(T0)).executeUpdate();
            usuarioId = ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = ?1")
                    .setParameter(1, CPF).getSingleResult()).longValue();
            entityManager.createNativeQuery("""
                            INSERT INTO chamado (usuario_id, modalidade, orgao, ocorrencia_id, latitude, longitude,
                                precisao, timestamp_localizacao, baixa_precisao, detalhe_adicional, estado,
                                data_criacao, chave_idempotencia)
                            VALUES (?1, 'EMERGENCIA_IMEDIATA', 'POLICIA_MILITAR', NULL, -15.79, -47.88, 25.0, ?2,
                                FALSE, NULL, 'CRIADO', ?2, 'chave-concorrencia')""")
                    .setParameter(1, usuarioId).setParameter(2, Timestamp.from(T0)).executeUpdate();
            chamadoId = ((Number) entityManager.createNativeQuery("SELECT id FROM chamado WHERE usuario_id = ?1")
                    .setParameter(1, usuarioId).getSingleResult()).longValue();
            adapter.salvar(Encaminhamento.agendar(chamadoId, null, null, T0, T0.plusSeconds(8)));
        });
    }

    @AfterEach
    void removerDados() {
        executor.shutdownNow();
        transacao.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM encaminhamento WHERE chamado_id = ?1")
                    .setParameter(1, chamadoId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM chamado WHERE id = ?1")
                    .setParameter(1, chamadoId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM usuario WHERE id = ?1")
                    .setParameter(1, usuarioId).executeUpdate();
        });
    }

    @Test
    void bloqueioDeveSerializarAlteracoesConcorrentesDoEncaminhamento() throws Exception {
        CountDownLatch primeiraComBloqueio = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);

        // 1ª transação: obtém o bloqueio, conclui e só confirma quando liberada.
        Future<?> primeira = executor.submit(() -> transacao.executeWithoutResult(status -> {
            Encaminhamento encaminhamento = adapter.buscarPorChamadoIdParaAlteracao(chamadoId).orElseThrow();
            encaminhamento.concluir();
            adapter.salvar(encaminhamento);
            primeiraComBloqueio.countDown();
            aguardar(liberarPrimeira);
        }));
        assertThat(primeiraComBloqueio.await(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)).isTrue();

        // 2ª transação: tenta concluir também e deve esperar o bloqueio da 1ª.
        Future<?> segunda = executor.submit(() -> transacao.executeWithoutResult(status -> {
            Encaminhamento encaminhamento = adapter.buscarPorChamadoIdParaAlteracao(chamadoId).orElseThrow();
            encaminhamento.concluir();
            adapter.salvar(encaminhamento);
        }));
        Thread.sleep(1_000);
        assertThat(segunda.isDone()).as("a 2ª transação deve estar bloqueada").isFalse();

        liberarPrimeira.countDown();
        primeira.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);

        // Após o commit da 1ª, a 2ª lê CONCLUIDO e o domínio rejeita a repetição (EN3).
        assertThatThrownBy(() -> segunda.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("ENCAMINHAMENTO_TRANSICAO_INVALIDA");
        Encaminhamento lido = adapter.buscarPorChamadoId(chamadoId).orElseThrow();
        assertThat(lido.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
        assertThat(lido.getProximaTransicaoEm()).isNull();
    }

    @Test
    void buscarParaAlteracaoDeveExigirTransacaoAtiva() {
        assertThatThrownBy(() -> adapter.buscarPorChamadoIdParaAlteracao(chamadoId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Tempo máximo de espera excedido.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
