package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.RelogioAjustavel;
import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.repository.EncaminhamentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Várias "instâncias do job" avançando o mesmo Chamado ao mesmo tempo, com transações reais: o bloqueio do Chamado
// serializa as tentativas e a reconferência sob bloqueio faz as demais não aplicarem nada.
@SpringBootTest
@Import({TestcontainersConfiguration.class, AvancoConcorrenteTest.RelogioDeTeste.class})
class AvancoConcorrenteTest {

    private static final Instant INICIO = Instant.parse("2031-06-01T10:00:00Z");
    private static final String CPF = "30000000205";
    private static final int CONCORRENTES = 6;

    @TestConfiguration
    static class RelogioDeTeste {

        @Bean
        @Primary
        RelogioAjustavel relogioAjustavel() {
            return new RelogioAjustavel(INICIO);
        }
    }

    @Autowired
    private EncaminhamentoService encaminhamentoService;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private EncaminhamentoRepository encaminhamentoRepository;

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private JdbcTemplate jdbc;

    private Long usuarioId;
    private Long chamadoId;
    private ExecutorService executor;

    @BeforeEach
    void preparar() {
        relogio.definir(INICIO);
        executor = Executors.newFixedThreadPool(CONCORRENTES);
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Usuário", CPF, "concorrencia-avanco@exemplo.com", "hash", true, Timestamp.from(INICIO));
        usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, CPF);
        chamadoId = chamadoRepository.salvarNovo(Chamado.registrarEmergenciaImediata(usuarioId,
                new Localizacao(-15.79, -47.88, 25.0f, INICIO), 100.0f, INICIO), "concorrencia").getId();
        encaminhamentoRepository.salvar(Encaminhamento.agendar(chamadoId, null, null, INICIO, INICIO.plusSeconds(8)));
    }

    @AfterEach
    void removerDados() {
        executor.shutdownNow();
        jdbc.update("DELETE FROM encaminhamento WHERE chamado_id = ?", chamadoId);
        jdbc.update("DELETE FROM chamado_transicao WHERE chamado_id = ?", chamadoId);
        jdbc.update("DELETE FROM chamado WHERE id = ?", chamadoId);
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuarioId);
    }

    private List<Boolean> avancarConcorrentemente() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> tentativas = new ArrayList<>();
        for (int i = 0; i < CONCORRENTES; i++) {
            tentativas.add(executor.submit(() -> {
                largada.await();
                return encaminhamentoService.avancar(chamadoId);
            }));
        }
        largada.countDown();
        List<Boolean> resultados = new ArrayList<>();
        for (Future<Boolean> tentativa : tentativas) {
            resultados.add(tentativa.get(30, TimeUnit.SECONDS));
        }
        return resultados;
    }

    private int transicoes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM chamado_transicao WHERE chamado_id = ?", Integer.class,
                chamadoId);
    }

    @RepeatedTest(3)
    void somenteUmaTentativaDeveAplicarCadaEtapa() throws Exception {
        relogio.definir(INICIO.plusSeconds(8));
        List<Boolean> primeiraEtapa = avancarConcorrentemente();

        assertThat(primeiraEtapa).containsOnlyOnce(true).hasSize(CONCORRENTES);
        assertThat(transicoes()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT estado FROM chamado WHERE id = ?", String.class, chamadoId))
                .isEqualTo("ENCAMINHADO_SIMULADO");

        relogio.definir(INICIO.plusSeconds(28));
        List<Boolean> segundaEtapa = avancarConcorrentemente();

        assertThat(segundaEtapa).containsOnlyOnce(true);
        assertThat(transicoes()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM encaminhamento WHERE chamado_id = ?", String.class,
                chamadoId)).isEqualTo("CONCLUIDO");
    }
}
