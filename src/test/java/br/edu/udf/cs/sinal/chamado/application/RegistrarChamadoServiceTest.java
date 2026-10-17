package br.edu.udf.cs.sinal.chamado.application;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.Transicao;
import br.edu.udf.cs.sinal.chamado.domain.repository.ChamadoRepository;
import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrarChamadoServiceTest {

    private static final Long USUARIO_ID = 1L;
    private static final Long OCORRENCIA_ID = 12L;
    private static final String CHAVE = "chave-abc_123";
    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00.123456Z");
    private static final Instant CAPTURA = Instant.parse("2026-10-17T11:59:58.987654321Z");
    private static final LocalizacaoInformada LOCALIZACAO =
            new LocalizacaoInformada(-15.7939, -47.8828, 25.5f, CAPTURA);

    private ChamadoRepository repository;
    private CatalogoDeOcorrencias catalogo;
    private ApplicationEventPublisher eventos;
    private PlatformTransactionManager transactionManager;
    private RegistrarChamadoService service;

    @BeforeEach
    void configurar() {
        repository = mock(ChamadoRepository.class);
        catalogo = mock(CatalogoDeOcorrencias.class);
        eventos = mock(ApplicationEventPublisher.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        service = new RegistrarChamadoService(repository, catalogo, eventos, Clock.fixed(AGORA, ZoneOffset.UTC),
                transactionManager, 100.0f);
        when(catalogo.ocorrenciaAtivaDoOrgao(OCORRENCIA_ID, OrgaoSeguranca.SAMU)).thenReturn(true);
        when(repository.buscarPorUsuarioEChaveIdempotencia(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repository.salvarNovo(any(), anyString())).thenAnswer(i -> comId(i.getArgument(0), 42L));
    }

    // Simula o adapter: devolve o Chamado com o id gerado.
    private static Chamado comId(Chamado chamado, Long id) {
        return Chamado.reconstituir(id, chamado.getUsuarioId(), chamado.getModalidade(), chamado.getOrgao(),
                chamado.getOcorrenciaId(), chamado.getLocalizacao(), chamado.getBaixaPrecisao(),
                chamado.getDetalheAdicional(), chamado.getEstado(), chamado.getDataCriacao(), chamado.getTransicoes());
    }

    private static Localizacao localizacaoTruncada() {
        return new Localizacao(-15.7939, -47.8828, 25.5f, Instant.parse("2026-10-17T11:59:58.987654Z"));
    }

    private static Chamado existentePadrao(OrgaoSeguranca orgao, Long ocorrenciaId, String detalhe,
                                           Localizacao localizacao) {
        Instant criado = AGORA.minusSeconds(30);
        return Chamado.reconstituir(7L, USUARIO_ID, ModalidadeChamado.PADRAO, orgao, ocorrenciaId, localizacao,
                false, detalhe, EstadoChamado.CRIADO, criado, List.of(new Transicao(EstadoChamado.CRIADO, criado)));
    }

    private ChamadoRegistradoResultado registrarPadrao(String detalhe) {
        return service.registrarPadrao(USUARIO_ID, CHAVE, OrgaoSeguranca.SAMU, OCORRENCIA_ID, detalhe, LOCALIZACAO);
    }

    private static void assertAplicacao(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOfSatisfying(AplicacaoException.class, e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    private static DataIntegrityViolationException violacao(String mensagemDoBanco) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(mensagemDoBanco));
    }

    @Nested
    class NovoRegistro {

        @Test
        void deveSalvarComAChaveEPublicarOEventoAntesDoCommit() {
            ChamadoRegistradoResultado resultado = registrarPadrao("  Pessoa no 2º andar  ");

            ArgumentCaptor<Chamado> salvo = ArgumentCaptor.forClass(Chamado.class);
            InOrder ordem = inOrder(repository, eventos, transactionManager);
            ordem.verify(repository).buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE);
            ordem.verify(repository).salvarNovo(salvo.capture(), eq(CHAVE));
            ordem.verify(eventos).publishEvent(new ChamadoRegistrado(42L, AGORA));
            ordem.verify(transactionManager).commit(any());

            assertThat(salvo.getValue().getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
            assertThat(salvo.getValue().getDetalheAdicional()).isEqualTo("Pessoa no 2º andar");
            assertThat(resultado.id()).isEqualTo(42L);
            assertThat(resultado.replay()).isFalse();
            assertThat(resultado.estado()).isEqualTo(EstadoChamado.CRIADO);
        }

        @Test
        void deveUsarOClockOLimiarConfiguradoEOTimestampTruncadoEmMicrossegundos() {
            ChamadoRegistradoResultado resultado = service.registrarPadrao(USUARIO_ID, CHAVE, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, null, new LocalizacaoInformada(-15.7939, -47.8828, 150.0f, CAPTURA));

            assertThat(resultado.dataCriacao()).isEqualTo(AGORA);
            assertThat(resultado.baixaPrecisao()).isTrue();
            assertThat(resultado.timestampLocalizacao()).isEqualTo(Instant.parse("2026-10-17T11:59:58.987654Z"));
        }

        @Test
        void emergenciaImediataDeveSerPmSemOcorrenciaESemConsultarOCatalogo() {
            ChamadoRegistradoResultado resultado = service.registrarEmergenciaImediata(USUARIO_ID, CHAVE, LOCALIZACAO);

            assertThat(resultado.modalidade()).isEqualTo(ModalidadeChamado.EMERGENCIA_IMEDIATA);
            assertThat(resultado.orgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
            assertThat(resultado.ocorrenciaId()).isNull();
            assertThat(resultado.detalheAdicional()).isNull();
            verifyNoInteractions(catalogo);
            verify(eventos).publishEvent(new ChamadoRegistrado(42L, AGORA));
        }

        @Test
        void falhaAoSalvarNaoDevePublicarEvento() {
            doThrow(new IllegalStateException("falha")).when(repository).salvarNovo(any(), anyString());

            assertThatThrownBy(() -> registrarPadrao(null)).isInstanceOf(IllegalStateException.class);
            verify(eventos, never()).publishEvent(any());
            verify(transactionManager).rollback(any());
        }
    }

    @Nested
    class Replay {

        @Test
        void mesmaChaveEMesmosDadosDevemDevolverOChamadoOriginalSemGravar() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, "Pessoa no 2º andar", localizacaoTruncada())));

            ChamadoRegistradoResultado resultado = registrarPadrao(" Pessoa no 2º andar ");

            assertThat(resultado.replay()).isTrue();
            assertThat(resultado.id()).isEqualTo(7L);
            verify(repository, never()).salvarNovo(any(), anyString());
            verifyNoInteractions(eventos);
        }

        @Test
        void replayDeveAcontecerAntesDaValidacaoDoCatalogo() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, null, localizacaoTruncada())));
            when(catalogo.ocorrenciaAtivaDoOrgao(OCORRENCIA_ID, OrgaoSeguranca.SAMU)).thenReturn(false);

            assertThat(registrarPadrao(null).replay()).isTrue();
            verifyNoInteractions(catalogo);
        }

        @Test
        void orgaoDiferenteDeveSerIdempotenciaDivergente() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.CORPO_DE_BOMBEIROS, OCORRENCIA_ID, null, localizacaoTruncada())));

            assertAplicacao(() -> registrarPadrao(null), "IDEMPOTENCIA_DIVERGENTE");
        }

        @Test
        void ocorrenciaDiferenteDeveSerIdempotenciaDivergente() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, 99L, null, localizacaoTruncada())));

            assertAplicacao(() -> registrarPadrao(null), "IDEMPOTENCIA_DIVERGENTE");
        }

        @Test
        void detalheDiferenteDeveSerIdempotenciaDivergente() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, "Outro", localizacaoTruncada())));

            assertAplicacao(() -> registrarPadrao(null), "IDEMPOTENCIA_DIVERGENTE");
        }

        @Test
        void localizacaoDiferenteDeveSerIdempotenciaDivergente() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, null,
                            new Localizacao(-15.7939, -47.8828, 30.0f, localizacaoTruncada().getTimestamp()))));

            assertAplicacao(() -> registrarPadrao(null), "IDEMPOTENCIA_DIVERGENTE");
        }

        @Test
        void modalidadeDiferenteDeveSerIdempotenciaDivergente() {
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE)).thenReturn(Optional.of(
                    existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, null, localizacaoTruncada())));

            assertAplicacao(() -> service.registrarEmergenciaImediata(USUARIO_ID, CHAVE, LOCALIZACAO),
                    "IDEMPOTENCIA_DIVERGENTE");
        }
    }

    @Nested
    class CondicaoDeCorrida {

        @Test
        void violacaoDaChaveDeveRelerEmNovaTransacaoEResponderComoReplay() {
            Chamado vencedor = existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, null, localizacaoTruncada());
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE))
                    .thenReturn(Optional.empty(), Optional.of(vencedor));
            doThrow(violacao("Duplicate entry '1-chave' for key 'chamado.uk_chamado_usuario_chave_idempotencia'"))
                    .when(repository).salvarNovo(any(), anyString());

            ChamadoRegistradoResultado resultado = registrarPadrao(null);

            assertThat(resultado.replay()).isTrue();
            assertThat(resultado.id()).isEqualTo(7L);
            verify(transactionManager).rollback(any());
            verifyNoInteractions(eventos);
        }

        @Test
        void corridaComDadosDiferentesDeveSerIdempotenciaDivergente() {
            Chamado vencedor = existentePadrao(OrgaoSeguranca.SAMU, OCORRENCIA_ID, "Outro", localizacaoTruncada());
            when(repository.buscarPorUsuarioEChaveIdempotencia(USUARIO_ID, CHAVE))
                    .thenReturn(Optional.empty(), Optional.of(vencedor));
            doThrow(violacao("Duplicate entry '1-chave' for key 'chamado.uk_chamado_usuario_chave_idempotencia'"))
                    .when(repository).salvarNovo(any(), anyString());

            assertAplicacao(() -> registrarPadrao(null), "IDEMPOTENCIA_DIVERGENTE");
        }

        @Test
        void outraViolacaoDeIntegridadeNaoDeveSerTraduzida() {
            DataIntegrityViolationException original = violacao("Cannot add or update a child row: fk_chamado_usuario");
            doThrow(original).when(repository).salvarNovo(any(), anyString());

            assertThatThrownBy(() -> registrarPadrao(null)).isSameAs(original);
        }
    }

    @Nested
    class Validacoes {

        @Test
        void ocorrenciaInvalidaDeveSerRejeitadaSemGravar() {
            when(catalogo.ocorrenciaAtivaDoOrgao(OCORRENCIA_ID, OrgaoSeguranca.SAMU)).thenReturn(false);

            assertThatThrownBy(() -> registrarPadrao(null))
                    .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                        assertThat(e.getCodigo()).isEqualTo("OCORRENCIA_INVALIDA");
                        assertThat(e.getCampos()).containsExactly("ocorrenciaId");
                    });
            verify(repository, never()).salvarNovo(any(), anyString());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void chaveAusenteDeveSerCampoObrigatorio(String chave) {
            assertThatThrownBy(() -> service.registrarEmergenciaImediata(USUARIO_ID, chave, LOCALIZACAO))
                    .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                        assertThat(e.getCodigo()).isEqualTo("CAMPO_OBRIGATORIO");
                        assertThat(e.getCampos()).containsExactly("Idempotency-Key");
                    });
            verifyNoInteractions(repository);
        }

        @Test
        void chaveComFormatoInvalidoDeveSerValorInvalido() {
            for (String chave : List.of("com espaço", "acentuação", "a/b", "x".repeat(101))) {
                assertAplicacao(() -> service.registrarEmergenciaImediata(USUARIO_ID, chave, LOCALIZACAO),
                        "VALOR_INVALIDO");
            }
            assertThat(service.registrarEmergenciaImediata(USUARIO_ID, "x".repeat(100), LOCALIZACAO).id())
                    .isEqualTo(42L);
        }

        @Test
        void localizacaoInvalidaDeveSerRejeitadaPeloDominioSemConsultarORepositorio() {
            assertThatThrownBy(() -> service.registrarEmergenciaImediata(USUARIO_ID, CHAVE,
                    new LocalizacaoInformada(91.0, -47.88, 25.0f, CAPTURA)))
                    .isInstanceOf(DominioException.class)
                    .extracting(e -> ((DominioException) e).getCodigo())
                    .isEqualTo("LOCALIZACAO_LATITUDE_INVALIDA");
            verifyNoInteractions(repository);
        }

        @Test
        void localizacaoAusenteDeveSerRejeitadaPeloDominio() {
            assertThatThrownBy(() -> service.registrarEmergenciaImediata(USUARIO_ID, CHAVE, null))
                    .isInstanceOf(DominioException.class)
                    .extracting(e -> ((DominioException) e).getCodigo())
                    .isEqualTo("CHAMADO_LOCALIZACAO_OBRIGATORIA");
        }

        @Test
        void toStringNaoDeveExporCoordenadas() {
            assertThat(LOCALIZACAO.toString()).doesNotContain("15.7939").doesNotContain("47.8828");
        }
    }
}
