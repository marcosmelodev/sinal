package br.edu.udf.cs.sinal.encaminhamento.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncaminhamentoTest {

    private static final Long CHAMADO_ID = 10L;
    private static final String JURISDICAO = "JURISDICAO-FICTICIA-01";
    private static final String CENTRAL = "C-PM";
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(8);
    private static final Instant T2 = T0.plusSeconds(28);

    private static Encaminhamento agendado() {
        return Encaminhamento.agendar(CHAMADO_ID, JURISDICAO, CENTRAL, T0, T1);
    }

    private static Encaminhamento concluido() {
        Encaminhamento encaminhamento = agendado();
        encaminhamento.concluir();
        return encaminhamento;
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    // Garante que uma operação rejeitada não altera status nem agendamento.
    private static void assertRejeitadaSemAlterar(Encaminhamento encaminhamento,
                                                  Consumer<Encaminhamento> operacao, String codigo) {
        StatusEncaminhamento statusAntes = encaminhamento.getStatus();
        Instant agendamentoAntes = encaminhamento.getProximaTransicaoEm();

        assertCodigo(() -> operacao.accept(encaminhamento), codigo);

        assertThat(encaminhamento.getStatus()).isEqualTo(statusAntes);
        assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(agendamentoAntes);
    }

    @Nested
    class Agendar {

        @Test
        void deveCriarAgendadoESimulado() {
            Encaminhamento encaminhamento = agendado();

            assertThat(encaminhamento.getId()).isNull();
            assertThat(encaminhamento.getChamadoId()).isEqualTo(CHAMADO_ID);
            assertThat(encaminhamento.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
            assertThat(encaminhamento.getJurisdicaoCodigo()).isEqualTo(JURISDICAO);
            assertThat(encaminhamento.getCentralCodigo()).isEqualTo(CENTRAL);
            assertThat(encaminhamento.getDataCriacao()).isEqualTo(T0);
            assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T1);
        }

        @Test
        void deveAceitarSemJurisdicaoNemCentral() {
            Encaminhamento encaminhamento = Encaminhamento.agendar(CHAMADO_ID, null, null, T0, T1);

            assertThat(encaminhamento.getJurisdicaoCodigo()).isNull();
            assertThat(encaminhamento.getCentralCodigo()).isNull();
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        void codigosAusentesOuEmBrancoDevemVirarNulos(String codigo) {
            Encaminhamento encaminhamento = Encaminhamento.agendar(CHAMADO_ID, codigo, codigo, T0, T1);

            assertThat(encaminhamento.getJurisdicaoCodigo()).isNull();
            assertThat(encaminhamento.getCentralCodigo()).isNull();
        }

        @Test
        void deveApararCodigos() {
            Encaminhamento encaminhamento = Encaminhamento.agendar(CHAMADO_ID, " " + JURISDICAO + " ",
                    "  " + CENTRAL, T0, T1);

            assertThat(encaminhamento.getJurisdicaoCodigo()).isEqualTo(JURISDICAO);
            assertThat(encaminhamento.getCentralCodigo()).isEqualTo(CENTRAL);
        }

        @Test
        void deveRejeitarChamadoNulo() {
            assertCodigo(() -> Encaminhamento.agendar(null, JURISDICAO, CENTRAL, T0, T1),
                    "ENCAMINHAMENTO_CHAMADO_OBRIGATORIO");
        }

        @Test
        void deveRejeitarDataCriacaoNula() {
            assertCodigo(() -> Encaminhamento.agendar(CHAMADO_ID, JURISDICAO, CENTRAL, null, T1),
                    "ENCAMINHAMENTO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarAgendamentoNulo() {
            assertCodigo(() -> Encaminhamento.agendar(CHAMADO_ID, JURISDICAO, CENTRAL, T0, null),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveRejeitarAgendamentoAnteriorACriacao() {
            assertCodigo(() -> Encaminhamento.agendar(CHAMADO_ID, JURISDICAO, CENTRAL, T0, T0.minusMillis(1)),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveAceitarAgendamentoIgualACriacao() {
            assertThat(Encaminhamento.agendar(CHAMADO_ID, JURISDICAO, CENTRAL, T0, T0).getProximaTransicaoEm())
                    .isEqualTo(T0);
        }
    }

    @Nested
    class Reagendar {

        @Test
        void deveAtualizarOHorarioMantendoAgendado() {
            Encaminhamento encaminhamento = agendado();

            encaminhamento.reagendar(T2);

            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T2);
        }

        @Test
        void naoDeveAlterarOsDemaisDados() {
            Encaminhamento encaminhamento = agendado();

            encaminhamento.reagendar(T2);

            assertThat(encaminhamento.getChamadoId()).isEqualTo(CHAMADO_ID);
            assertThat(encaminhamento.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
            assertThat(encaminhamento.getJurisdicaoCodigo()).isEqualTo(JURISDICAO);
            assertThat(encaminhamento.getCentralCodigo()).isEqualTo(CENTRAL);
            assertThat(encaminhamento.getDataCriacao()).isEqualTo(T0);
        }

        @Test
        void deveAceitarHorarioIgualACriacao() {
            Encaminhamento encaminhamento = agendado();

            encaminhamento.reagendar(T0);

            assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T0);
        }

        @Test
        void deveRejeitarHorarioNulo() {
            assertRejeitadaSemAlterar(agendado(), e -> e.reagendar(null), "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveRejeitarHorarioAnteriorACriacao() {
            assertRejeitadaSemAlterar(agendado(), e -> e.reagendar(T0.minusSeconds(1)),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveRejeitarEmConcluido() {
            assertRejeitadaSemAlterar(concluido(), e -> e.reagendar(T2), "ENCAMINHAMENTO_TRANSICAO_INVALIDA");
        }
    }

    @Nested
    class Concluir {

        @Test
        void deveConcluirELimparOAgendamento() {
            Encaminhamento encaminhamento = agendado();

            encaminhamento.concluir();

            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isNull();
        }

        @Test
        void deveConcluirDepoisDeReagendar() {
            Encaminhamento encaminhamento = agendado();
            encaminhamento.reagendar(T2);

            encaminhamento.concluir();

            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isNull();
        }

        @Test
        void deveRejeitarConcluirDuasVezes() {
            assertRejeitadaSemAlterar(concluido(), Encaminhamento::concluir, "ENCAMINHAMENTO_TRANSICAO_INVALIDA");
        }

        @Test
        void naoDeveVoltarAAgendado() {
            Encaminhamento encaminhamento = concluido();

            assertCodigo(() -> encaminhamento.reagendar(T2), "ENCAMINHAMENTO_TRANSICAO_INVALIDA");
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
        }
    }

    @Nested
    class Reconstituir {

        @Test
        void devePreservarEncaminhamentoAgendado() {
            Encaminhamento encaminhamento = Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, T1);

            assertThat(encaminhamento.getId()).isEqualTo(5L);
            assertThat(encaminhamento.getChamadoId()).isEqualTo(CHAMADO_ID);
            assertThat(encaminhamento.getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
            assertThat(encaminhamento.getJurisdicaoCodigo()).isEqualTo(JURISDICAO);
            assertThat(encaminhamento.getCentralCodigo()).isEqualTo(CENTRAL);
            assertThat(encaminhamento.getDataCriacao()).isEqualTo(T0);
            assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T1);
        }

        @Test
        void devePreservarEncaminhamentoConcluidoSemJurisdicao() {
            Encaminhamento encaminhamento = Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.CONCLUIDO, null, null, T0, null);

            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isNull();
            assertThat(encaminhamento.getJurisdicaoCodigo()).isNull();
            assertThat(encaminhamento.getCentralCodigo()).isNull();
        }

        @Test
        void encaminhamentoReconstituidoConcluidoDeveSerTerminal() {
            Encaminhamento encaminhamento = Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.CONCLUIDO, JURISDICAO, CENTRAL, T0, null);

            assertRejeitadaSemAlterar(encaminhamento, e -> e.reagendar(T2), "ENCAMINHAMENTO_TRANSICAO_INVALIDA");
        }

        @Test
        void deveRejeitarIdNulo() {
            assertThatThrownBy(() -> Encaminhamento.reconstituir(null, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, T1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarModoNulo() {
            assertThatThrownBy(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, null,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, T1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarStatusNulo() {
            assertThatThrownBy(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    null, JURISDICAO, CENTRAL, T0, T1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarChamadoNulo() {
            assertCodigo(() -> Encaminhamento.reconstituir(5L, null, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, T1), "ENCAMINHAMENTO_CHAMADO_OBRIGATORIO");
        }

        @Test
        void deveRejeitarDataCriacaoNula() {
            assertCodigo(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.CONCLUIDO, JURISDICAO, CENTRAL, null, null), "ENCAMINHAMENTO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarAgendadoSemHorario() {
            assertCodigo(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, null),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveRejeitarAgendadoComHorarioAnteriorACriacao() {
            assertCodigo(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.AGENDADO, JURISDICAO, CENTRAL, T0, T0.minusSeconds(1)),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }

        @Test
        void deveRejeitarConcluidoComHorario() {
            assertCodigo(() -> Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO,
                    StatusEncaminhamento.CONCLUIDO, JURISDICAO, CENTRAL, T0, T1),
                    "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO");
        }
    }

    @Nested
    class EN2ModoSimulado {

        @Test
        void modoDeveTerSomenteSimulado() {
            assertThat(ModoEncaminhamento.values()).containsExactly(ModoEncaminhamento.SIMULADO);
        }

        @Test
        void agendarNaoDeveReceberOModo() {
            Method agendar = Arrays.stream(Encaminhamento.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("agendar"))
                    .findFirst().orElseThrow();

            assertThat(agendar.getParameterTypes()).doesNotContain(ModoEncaminhamento.class);
        }

        @Test
        void statusDeveTerSomenteAgendadoEConcluido() {
            assertThat(StatusEncaminhamento.values())
                    .containsExactly(StatusEncaminhamento.AGENDADO, StatusEncaminhamento.CONCLUIDO);
        }
    }

    @Nested
    class EN4SemAtendimentoReal {

        private static final List<String> TERMOS_PROIBIDOS = List.of("ATEND", "RECEB", "ACEIT", "RESOLV");

        @Test
        void nomesDoModeloNaoDevemSugerirAtendimentoReal() {
            Stream<String> nomes = Stream.of(
                    Arrays.stream(ModoEncaminhamento.values()).map(Enum::name),
                    Arrays.stream(StatusEncaminhamento.values()).map(Enum::name),
                    Arrays.stream(Encaminhamento.class.getDeclaredMethods()).map(Method::getName),
                    Arrays.stream(Encaminhamento.class.getDeclaredFields()).map(Field::getName)
            ).flatMap(s -> s);

            assertThat(nomes.map(nome -> nome.toUpperCase(Locale.ROOT)))
                    .allSatisfy(nome -> assertThat(TERMOS_PROIBIDOS).noneMatch(nome::contains));
        }
    }

    @Nested
    class Imutabilidade {

        @Test
        void naoDeveExporMetodosPublicosAlemDasOperacoes() {
            assertThat(Arrays.stream(Encaminhamento.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName))
                    .noneMatch(nome -> nome.startsWith("set"))
                    .filteredOn(nome -> !nome.startsWith("get"))
                    .containsExactlyInAnyOrder("agendar", "reconstituir", "reagendar", "concluir");
        }

        @Test
        void naoDeveExporConstrutorPublico() {
            assertThat(Encaminhamento.class.getDeclaredConstructors())
                    .allMatch(c -> Modifier.isPrivate(c.getModifiers()));
        }

        @Test
        void somenteStatusEAgendamentoPodemMudar() {
            assertThat(Encaminhamento.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .filteredOn(f -> !Modifier.isFinal(f.getModifiers()))
                    .extracting(Field::getName)
                    .containsExactlyInAnyOrder("status", "proximaTransicaoEm");
        }
    }
}
