package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.RelogioAjustavel;
import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.Encaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.ModoEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.model.StatusEncaminhamento;
import br.edu.udf.cs.sinal.encaminhamento.domain.repository.EncaminhamentoRepository;
import br.edu.udf.cs.sinal.encaminhamento.infrastructure.simulacao.EncaminhamentoSimuladoAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EncaminhamentoServiceTest {

    private static final Long CHAMADO_ID = 10L;
    private static final Instant T0 = Instant.parse("2026-10-17T12:00:00.123456Z");
    private static final Localizacao LOCALIZACAO = new Localizacao(-15.79, -47.88, 25.0f, T0);

    private ChamadoRepository chamados;
    private EncaminhamentoRepository encaminhamentos;
    private DeterminacaoDeCentralService determinacao;
    private PlatformTransactionManager transactionManager;
    private RelogioAjustavel relogio;
    private EncaminhamentoService service;

    @BeforeEach
    void configurar() {
        chamados = mock(ChamadoRepository.class);
        encaminhamentos = mock(EncaminhamentoRepository.class);
        determinacao = mock(DeterminacaoDeCentralService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        relogio = new RelogioAjustavel(T0.plusSeconds(1));
        service = new EncaminhamentoService(chamados, encaminhamentos,
                new EncaminhamentoSimuladoAdapter("PT8S", "PT20S"), determinacao, relogio, transactionManager);
        when(determinacao.determinar(any(), any())).thenReturn(new CentralDeterminada("JUR-1", "C-SAMU"));
    }

    private static Chamado chamado(EstadoChamado estado) {
        List<Transicao> transicoes = new ArrayList<>(List.of(new Transicao(EstadoChamado.CRIADO, T0)));
        if (estado != EstadoChamado.CRIADO) {
            transicoes.add(new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T0.plusSeconds(9)));
        }
        if (estado == EstadoChamado.ENCERRADO) {
            transicoes.add(new Transicao(EstadoChamado.ENCERRADO, T0.plusSeconds(29)));
        }
        return Chamado.reconstituir(CHAMADO_ID, 1L, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU, 12L, LOCALIZACAO,
                false, null, estado, T0, transicoes);
    }

    private static Encaminhamento agendado(Instant proxima) {
        return Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO, StatusEncaminhamento.AGENDADO,
                "JUR-1", "C-SAMU", T0.plusSeconds(1), proxima);
    }

    private static Encaminhamento concluido() {
        return Encaminhamento.reconstituir(5L, CHAMADO_ID, ModoEncaminhamento.SIMULADO, StatusEncaminhamento.CONCLUIDO,
                "JUR-1", "C-SAMU", T0.plusSeconds(1), null);
    }

    @Nested
    class Iniciar {

        @BeforeEach
        void chamadoCriadoSemEncaminhamento() {
            when(chamados.buscarPorId(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.CRIADO)));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.CRIADO)));
            when(encaminhamentos.buscarPorChamadoId(CHAMADO_ID)).thenReturn(Optional.empty());
        }

        @Test
        void deveDeterminarACentralForaDoBloqueioEAgendarOEncaminhamento() {
            assertThat(service.iniciar(CHAMADO_ID)).isTrue();

            InOrder ordem = inOrder(determinacao, chamados, encaminhamentos);
            ordem.verify(determinacao).determinar(LOCALIZACAO, OrgaoSeguranca.SAMU);
            ordem.verify(chamados).buscarPorIdParaAlteracao(CHAMADO_ID);
            ArgumentCaptor<Encaminhamento> salvo = ArgumentCaptor.forClass(Encaminhamento.class);
            ordem.verify(encaminhamentos).salvar(salvo.capture());
            assertThat(salvo.getValue().getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
            assertThat(salvo.getValue().getModo()).isEqualTo(ModoEncaminhamento.SIMULADO);
            assertThat(salvo.getValue().getJurisdicaoCodigo()).isEqualTo("JUR-1");
            assertThat(salvo.getValue().getCentralCodigo()).isEqualTo("C-SAMU");
            assertThat(salvo.getValue().getDataCriacao()).isEqualTo(T0.plusSeconds(1));
            assertThat(salvo.getValue().getProximaTransicaoEm()).isEqualTo(T0.plusSeconds(9));
        }

        @Test
        void semCentralDeveAgendarComCodigosNulos() {
            when(determinacao.determinar(any(), any())).thenReturn(CentralDeterminada.NENHUMA);

            service.iniciar(CHAMADO_ID);

            ArgumentCaptor<Encaminhamento> salvo = ArgumentCaptor.forClass(Encaminhamento.class);
            verify(encaminhamentos).salvar(salvo.capture());
            assertThat(salvo.getValue().getJurisdicaoCodigo()).isNull();
            assertThat(salvo.getValue().getCentralCodigo()).isNull();
        }

        @Test
        void chamadoInexistenteOuForaDeCriadoNaoDeveIniciar() {
            when(chamados.buscarPorId(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.ENCAMINHADO_SIMULADO)));
            assertThat(service.iniciar(CHAMADO_ID)).isFalse();

            when(chamados.buscarPorId(CHAMADO_ID)).thenReturn(Optional.empty());
            assertThat(service.iniciar(CHAMADO_ID)).isFalse();

            verifyNoInteractions(determinacao);
            verify(chamados, never()).buscarPorIdParaAlteracao(any());
        }

        @Test
        void encaminhamentoJaExistenteNaoDeveSerRecriado() {
            when(encaminhamentos.buscarPorChamadoId(CHAMADO_ID)).thenReturn(Optional.of(agendado(T0.plusSeconds(9))));

            assertThat(service.iniciar(CHAMADO_ID)).isFalse();
            verify(encaminhamentos, never()).salvar(any());
        }

        @Test
        void encaminhamentoCriadoEnquantoACentralEraDeterminadaNaoDeveSerDuplicado() {
            when(encaminhamentos.buscarPorChamadoId(CHAMADO_ID))
                    .thenReturn(Optional.empty(), Optional.of(agendado(T0.plusSeconds(9))));

            assertThat(service.iniciar(CHAMADO_ID)).isFalse();
            verify(encaminhamentos, never()).salvar(any());
        }

        @Test
        void violacaoDoEn1DeveSerTratadaComoJaExistente() {
            doThrow(new DataIntegrityViolationException("x", new SQLIntegrityConstraintViolationException(
                    "Duplicate entry '10' for key 'encaminhamento.uk_encaminhamento_chamado'")))
                    .when(encaminhamentos).salvar(any());

            assertThat(service.iniciar(CHAMADO_ID)).isFalse();
            verify(transactionManager).rollback(any());
        }

        @Test
        void outraViolacaoDeIntegridadeDevePropagar() {
            DataIntegrityViolationException original = new DataIntegrityViolationException("x",
                    new SQLIntegrityConstraintViolationException("fk_encaminhamento_chamado"));
            doThrow(original).when(encaminhamentos).salvar(any());

            assertThatThrownBy(() -> service.iniciar(CHAMADO_ID)).isSameAs(original);
        }
    }

    @Nested
    class Avancar {

        @Test
        void primeiraEtapaDeveEncaminharOChamadoEReagendarComBloqueioNaOrdem() {
            relogio.definir(T0.plusSeconds(9));
            Chamado criado = chamado(EstadoChamado.CRIADO);
            Encaminhamento encaminhamento = agendado(T0.plusSeconds(9));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(criado));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(encaminhamento));

            assertThat(service.avancar(CHAMADO_ID)).isTrue();

            InOrder ordem = inOrder(chamados, encaminhamentos);
            ordem.verify(chamados).buscarPorIdParaAlteracao(CHAMADO_ID);
            ordem.verify(encaminhamentos).buscarPorChamadoIdParaAlteracao(CHAMADO_ID);
            ordem.verify(chamados).salvar(criado);
            ordem.verify(encaminhamentos).salvar(encaminhamento);
            assertThat(criado.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
            assertThat(criado.getTransicoes()).last().isEqualTo(
                    new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T0.plusSeconds(9)));
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.AGENDADO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isEqualTo(T0.plusSeconds(29));
        }

        @Test
        void segundaEtapaDeveEncerrarOChamadoEConcluirOEncaminhamento() {
            relogio.definir(T0.plusSeconds(30));
            Chamado encaminhadoSimulado = chamado(EstadoChamado.ENCAMINHADO_SIMULADO);
            Encaminhamento encaminhamento = agendado(T0.plusSeconds(29));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(encaminhadoSimulado));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(encaminhamento));

            assertThat(service.avancar(CHAMADO_ID)).isTrue();

            assertThat(encaminhadoSimulado.getEstado()).isEqualTo(EstadoChamado.ENCERRADO);
            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
            assertThat(encaminhamento.getProximaTransicaoEm()).isNull();
            verify(chamados).salvar(encaminhadoSimulado);
            verify(encaminhamentos).salvar(encaminhamento);
        }

        @Test
        void horarioIgualAoAgendadoDeveContarComoVencido() {
            relogio.definir(T0.plusSeconds(9));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.CRIADO)));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID))
                    .thenReturn(Optional.of(agendado(T0.plusSeconds(9))));

            assertThat(service.avancar(CHAMADO_ID)).isTrue();
        }

        @Test
        void antesDoHorarioNaoDeveAvancar() {
            relogio.definir(T0.plusSeconds(8));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.CRIADO)));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID))
                    .thenReturn(Optional.of(agendado(T0.plusSeconds(9))));

            assertThat(service.avancar(CHAMADO_ID)).isFalse();
            verify(chamados, never()).salvar(any());
            verify(encaminhamentos, never()).salvar(any());
        }

        @Test
        void encaminhamentoConcluidoOuAusenteNaoDeveAvancar() {
            relogio.definir(T0.plusSeconds(60));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.ENCERRADO)));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(concluido()));
            assertThat(service.avancar(CHAMADO_ID)).isFalse();

            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.empty());
            assertThat(service.avancar(CHAMADO_ID)).isFalse();

            verify(chamados, never()).salvar(any());
            verify(encaminhamentos, never()).salvar(any());
        }

        @Test
        void chamadoInexistenteNaoDeveBloquearOEncaminhamento() {
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.empty());

            assertThat(service.avancar(CHAMADO_ID)).isFalse();
            verify(encaminhamentos, never()).buscarPorChamadoIdParaAlteracao(any());
        }

        @Test
        void chamadoJaEncerradoComEncaminhamentoAgendadoDeveSomenteConcluir() {
            relogio.definir(T0.plusSeconds(60));
            Encaminhamento encaminhamento = agendado(T0.plusSeconds(29));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.ENCERRADO)));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(encaminhamento));

            assertThat(service.avancar(CHAMADO_ID)).isTrue();

            assertThat(encaminhamento.getStatus()).isEqualTo(StatusEncaminhamento.CONCLUIDO);
            verify(chamados, never()).salvar(any());
            verify(encaminhamentos).salvar(encaminhamento);
        }

        @Test
        void atrasoDeEncerramentoDeveVirDoGatewaySimulado() {
            relogio.definir(T0.plusSeconds(9));
            Encaminhamento encaminhamento = agendado(T0.plusSeconds(9));
            when(chamados.buscarPorIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(chamado(EstadoChamado.CRIADO)));
            when(encaminhamentos.buscarPorChamadoIdParaAlteracao(CHAMADO_ID)).thenReturn(Optional.of(encaminhamento));

            service.avancar(CHAMADO_ID);

            assertThat(Duration.between(T0.plusSeconds(9), encaminhamento.getProximaTransicaoEm()))
                    .isEqualTo(Duration.ofSeconds(20));
        }
    }
}
