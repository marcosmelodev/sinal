package br.edu.udf.cs.sinal.chamado.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
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
@Import({TestcontainersConfiguration.class, ChamadoPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChamadoConcorrenciaTest {

    private static final String CPF = "12345678909";
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");
    private static final long TEMPO_MAXIMO_SEGUNDOS = 10;

    @Autowired
    private ChamadoPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transacao;
    private ExecutorService executor;
    private Long usuarioId;
    private Long chamadoId;

    @BeforeEach
    void prepararChamadoCriado() {
        transacao = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        transacao.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                            VALUES ('Usuário', ?1, 'concorrencia-chamado@exemplo.com', 'hash', TRUE, ?2)""")
                    .setParameter(1, CPF).setParameter(2, Timestamp.from(T0)).executeUpdate();
            usuarioId = ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = ?1")
                    .setParameter(1, CPF).getSingleResult()).longValue();
            Chamado chamado = Chamado.registrarEmergenciaImediata(usuarioId,
                    new Localizacao(-15.79, -47.88, 25.0f, T0), 100.0f, T0);
            chamadoId = adapter.salvarNovo(chamado, "chave-concorrencia").getId();
        });
    }

    @AfterEach
    void removerDados() {
        executor.shutdownNow();
        transacao.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM chamado_transicao WHERE chamado_id = ?1")
                    .setParameter(1, chamadoId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM chamado WHERE id = ?1")
                    .setParameter(1, chamadoId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM usuario WHERE id = ?1")
                    .setParameter(1, usuarioId).executeUpdate();
        });
    }

    @Test
    void bloqueioDeveSerializarTransicoesConcorrentesDoMesmoChamado() throws Exception {
        CountDownLatch primeiraComBloqueio = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);

        // 1ª transação: obtém o bloqueio, encaminha e só confirma quando liberada.
        Future<?> primeira = executor.submit(() -> transacao.executeWithoutResult(status -> {
            Chamado chamado = adapter.buscarPorIdParaAlteracao(chamadoId).orElseThrow();
            chamado.encaminharSimulado(T0.plusSeconds(8));
            adapter.salvar(chamado);
            primeiraComBloqueio.countDown();
            aguardar(liberarPrimeira);
        }));
        assertThat(primeiraComBloqueio.await(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)).isTrue();

        // 2ª transação: tenta a mesma transição e deve esperar o bloqueio da 1ª.
        Future<?> segunda = executor.submit(() -> transacao.executeWithoutResult(status -> {
            Chamado chamado = adapter.buscarPorIdParaAlteracao(chamadoId).orElseThrow();
            chamado.encaminharSimulado(T0.plusSeconds(9));
            adapter.salvar(chamado);
        }));
        Thread.sleep(1_000);
        assertThat(segunda.isDone()).as("a 2ª transação deve estar bloqueada").isFalse();

        liberarPrimeira.countDown();
        primeira.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);

        // Após o commit da 1ª, a 2ª lê ENCAMINHADO_SIMULADO e o domínio rejeita a repetição (CH7).
        assertThatThrownBy(() -> segunda.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("CHAMADO_TRANSICAO_INVALIDA");
        Chamado lido = adapter.buscarPorId(chamadoId).orElseThrow();
        assertThat(lido.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
        assertThat(lido.getTransicoes()).hasSize(2);
    }

    @Test
    void buscarParaAlteracaoDeveExigirTransacaoAtiva() {
        assertThatThrownBy(() -> adapter.buscarPorIdParaAlteracao(chamadoId))
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
