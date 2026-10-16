package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
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
@Import({TestcontainersConfiguration.class, ContatosPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContatosConcorrenciaTest {

    private static final String CPF = "93541134780";
    private static final String TELEFONE = "61999998888";
    private static final long TEMPO_MAXIMO_SEGUNDOS = 10;

    @Autowired
    private ContatosPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transacao;
    private ExecutorService executor;
    private Long usuarioId;

    @BeforeEach
    void prepararUsuarioComQuatroContatos() {
        transacao = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        usuarioId = transacao.execute(status -> {
            entityManager.createNativeQuery("""
                            INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                            VALUES ('Usuário', ?1, 'concorrencia@exemplo.com', 'hash', TRUE, ?2)""")
                    .setParameter(1, CPF).setParameter(2, Timestamp.from(Instant.parse("2026-10-15T12:00:00Z")))
                    .executeUpdate();
            Long id = ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = ?1")
                    .setParameter(1, CPF).getSingleResult()).longValue();
            ContatosDeConfianca contatos = ContatosDeConfianca.novo(id);
            for (int i = 1; i <= 4; i++) {
                contatos.adicionar("Contato " + i, TELEFONE);
            }
            adapter.salvar(contatos);
            return id;
        });
    }

    @AfterEach
    void removerDados() {
        executor.shutdownNow();
        transacao.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM contato_confianca WHERE usuario_id = ?1")
                    .setParameter(1, usuarioId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM usuario WHERE id = ?1")
                    .setParameter(1, usuarioId).executeUpdate();
        });
    }

    private int contatosNoBanco() {
        return adapter.buscarPorUsuario(usuarioId).getContatos().size();
    }

    @Test
    void bloqueioDeveSerializarAdicoesConcorrentesEProtegerOLimite() throws Exception {
        CountDownLatch primeiraComBloqueio = new CountDownLatch(1);
        CountDownLatch liberarPrimeira = new CountDownLatch(1);

        // 1ª transação: obtém o bloqueio, adiciona o 5º contato e só confirma quando liberada.
        Future<?> primeira = executor.submit(() -> transacao.executeWithoutResult(status -> {
            ContatosDeConfianca contatos = adapter.buscarPorUsuarioParaAlteracao(usuarioId);
            contatos.adicionar("Quinto", TELEFONE);
            adapter.salvar(contatos);
            primeiraComBloqueio.countDown();
            aguardar(liberarPrimeira);
        }));
        assertThat(primeiraComBloqueio.await(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS)).isTrue();

        // 2ª transação: tenta o mesmo e deve esperar o bloqueio da 1ª.
        Future<?> segunda = executor.submit(() -> transacao.executeWithoutResult(status -> {
            ContatosDeConfianca contatos = adapter.buscarPorUsuarioParaAlteracao(usuarioId);
            contatos.adicionar("Sexto", TELEFONE);
            adapter.salvar(contatos);
        }));
        Thread.sleep(1_000);
        assertThat(segunda.isDone()).as("a 2ª transação deve estar bloqueada").isFalse();

        liberarPrimeira.countDown();
        primeira.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);

        // Após o commit da 1ª, a 2ª lê 5 contatos e o domínio rejeita o 6º.
        assertThatThrownBy(() -> segunda.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("LIMITE_CONTATOS_EXCEDIDO");
        assertThat(contatosNoBanco()).isEqualTo(5);
    }

    // Documenta por que o bloqueio é necessário: a leitura comum permite violar o limite.
    @Test
    void semBloqueioAsDuasTransacoesUltrapassariamOLimite() throws Exception {
        CountDownLatch ambasLeram = new CountDownLatch(2);

        Runnable adicionarSemBloqueio = () -> transacao.executeWithoutResult(status -> {
            ContatosDeConfianca contatos = adapter.buscarPorUsuario(usuarioId);
            ambasLeram.countDown();
            aguardar(ambasLeram);
            contatos.adicionar("Extra", TELEFONE);
            adapter.salvar(contatos);
        });
        Future<?> primeira = executor.submit(adicionarSemBloqueio);
        Future<?> segunda = executor.submit(adicionarSemBloqueio);
        primeira.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);
        segunda.get(TEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);

        Integer linhas = transacao.execute(status -> ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM contato_confianca WHERE usuario_id = ?1")
                .setParameter(1, usuarioId).getSingleResult()).intValue());
        assertThat(linhas).isEqualTo(6);
    }

    @Test
    void buscarParaAlteracaoDeveExigirTransacaoAtiva() {
        assertThatThrownBy(() -> adapter.buscarPorUsuarioParaAlteracao(usuarioId))
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
