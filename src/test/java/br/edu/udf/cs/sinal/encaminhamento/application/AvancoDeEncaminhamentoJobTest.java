package br.edu.udf.cs.sinal.encaminhamento.application;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvancoDeEncaminhamentoJobTest {

    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");

    private EncaminhamentoRepository encaminhamentos;
    private ChamadoRepository chamados;
    private EncaminhamentoService service;
    private AvancoDeEncaminhamentoJob job;

    @BeforeEach
    void configurar() {
        encaminhamentos = mock(EncaminhamentoRepository.class);
        chamados = mock(ChamadoRepository.class);
        service = mock(EncaminhamentoService.class);
        job = new AvancoDeEncaminhamentoJob(encaminhamentos, chamados, service, Clock.fixed(AGORA, ZoneOffset.UTC),
                50, "PT30S");
        when(encaminhamentos.buscarAgendadosVencidos(AGORA, 50)).thenReturn(List.of());
        when(chamados.buscarCriadosSemEncaminhamento(AGORA.minusSeconds(30), 50)).thenReturn(List.of());
    }

    private static Encaminhamento vencido(Long chamadoId) {
        return Encaminhamento.reconstituir(chamadoId + 100, chamadoId, ModoEncaminhamento.SIMULADO,
                StatusEncaminhamento.AGENDADO, null, null, AGORA.minusSeconds(60), AGORA.minusSeconds(1));
    }

    private static Chamado criado(Long id) {
        Instant criadoEm = AGORA.minusSeconds(120);
        return Chamado.reconstituir(id, 1L, ModalidadeChamado.EMERGENCIA_IMEDIATA, OrgaoSeguranca.POLICIA_MILITAR,
                null, new Localizacao(-15.79, -47.88, 25.0f, criadoEm), false, null, EstadoChamado.CRIADO, criadoEm,
                List.of(new Transicao(EstadoChamado.CRIADO, criadoEm)));
    }

    @Test
    void deveAvancarOsVencidosEDepoisReconciliarComCarencia() {
        when(encaminhamentos.buscarAgendadosVencidos(AGORA, 50)).thenReturn(List.of(vencido(1L), vencido(2L)));
        when(chamados.buscarCriadosSemEncaminhamento(AGORA.minusSeconds(30), 50)).thenReturn(List.of(criado(3L)));
        when(service.avancar(1L)).thenReturn(true);
        when(service.avancar(2L)).thenReturn(false);
        when(service.iniciar(3L)).thenReturn(true);

        AvancoDeEncaminhamentoJob.ResultadoDoCiclo resultado = job.executar();

        InOrder ordem = inOrder(service);
        ordem.verify(service).avancar(1L);
        ordem.verify(service).avancar(2L);
        ordem.verify(service).iniciar(3L);
        assertThat(resultado).isEqualTo(new AvancoDeEncaminhamentoJob.ResultadoDoCiclo(1, 1, 0));
    }

    @Test
    void falhaDeUmItemNaoDeveInterromperOLoteNemAReconciliacao() {
        when(encaminhamentos.buscarAgendadosVencidos(AGORA, 50)).thenReturn(List.of(vencido(1L), vencido(2L)));
        when(chamados.buscarCriadosSemEncaminhamento(AGORA.minusSeconds(30), 50))
                .thenReturn(List.of(criado(3L), criado(4L)));
        when(service.avancar(1L)).thenThrow(new IllegalStateException("falha"));
        when(service.avancar(2L)).thenReturn(true);
        when(service.iniciar(3L)).thenThrow(new IllegalStateException("falha"));
        when(service.iniciar(4L)).thenReturn(true);

        assertThat(job.executar()).isEqualTo(new AvancoDeEncaminhamentoJob.ResultadoDoCiclo(1, 1, 2));
        verify(service).avancar(2L);
        verify(service).iniciar(4L);
    }

    @Test
    void cicloSemTrabalhoNaoDeveChamarOServico() {
        assertThat(job.executar()).isEqualTo(new AvancoDeEncaminhamentoJob.ResultadoDoCiclo(0, 0, 0));
        verify(encaminhamentos).buscarAgendadosVencidos(AGORA, 50);
        verify(chamados).buscarCriadosSemEncaminhamento(AGORA.minusSeconds(30), 50);
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
