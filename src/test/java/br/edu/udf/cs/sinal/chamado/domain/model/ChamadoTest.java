package br.edu.udf.cs.sinal.chamado.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChamadoTest {

    private static final Long USUARIO_ID = 1L;
    private static final Long OCORRENCIA_ID = 10L;
    private static final Float LIMIAR = 100.0f;
    private static final Instant T0 = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(5);
    private static final Instant T2 = T0.plusSeconds(30);

    private static Localizacao localizacaoComPrecisao(Float precisao) {
        return new Localizacao(-15.7939, -47.8828, precisao, T0);
    }

    private static final Localizacao LOCALIZACAO = localizacaoComPrecisao(25.0f);

    private static Chamado padrao() {
        return Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID, LOCALIZACAO, null, LIMIAR, T0);
    }

    private static Chamado padraoComDetalhe(String detalhe) {
        return Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID, LOCALIZACAO, detalhe, LIMIAR, T0);
    }

    private static Chamado emergenciaImediata() {
        return Chamado.registrarEmergenciaImediata(USUARIO_ID, LOCALIZACAO, LIMIAR, T0);
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    // Garante que uma operação rejeitada não altera estado nem histórico.
    private static void assertRejeitadaSemAlterar(Chamado chamado, Consumer<Chamado> operacao, String codigo) {
        EstadoChamado estadoAntes = chamado.getEstado();
        List<Transicao> transicoesAntes = chamado.getTransicoes();

        assertCodigo(() -> operacao.accept(chamado), codigo);

        assertThat(chamado.getEstado()).isEqualTo(estadoAntes);
        assertThat(chamado.getTransicoes()).isEqualTo(transicoesAntes);
    }

    @Nested
    class RegistroPadrao {

        @ParameterizedTest
        @EnumSource(OrgaoSeguranca.class)
        void deveRegistrarPreservandoOsDados(OrgaoSeguranca orgao) {
            Chamado chamado = Chamado.registrar(USUARIO_ID, orgao, OCORRENCIA_ID, LOCALIZACAO, "Detalhe", LIMIAR, T0);

            assertThat(chamado.getId()).isNull();
            assertThat(chamado.getUsuarioId()).isEqualTo(USUARIO_ID);
            assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
            assertThat(chamado.getOrgao()).isEqualTo(orgao);
            assertThat(chamado.getOcorrenciaId()).isEqualTo(OCORRENCIA_ID);
            assertThat(chamado.getLocalizacao()).isEqualTo(LOCALIZACAO);
            assertThat(chamado.getDetalheAdicional()).isEqualTo("Detalhe");
            assertThat(chamado.getDataCriacao()).isEqualTo(T0);
        }

        @Test
        void deveRejeitarInstanteNulo() {
            assertCodigo(() -> Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID, LOCALIZACAO,
                    null, LIMIAR, null), "CHAMADO_INSTANTE_INVALIDO");
        }
    }

    @Nested
    class CH1UsuarioELocalizacao {

        @Test
        void padraoDeveRejeitarUsuarioNulo() {
            assertCodigo(() -> Chamado.registrar(null, OrgaoSeguranca.SAMU, OCORRENCIA_ID, LOCALIZACAO,
                    null, LIMIAR, T0), "CHAMADO_USUARIO_OBRIGATORIO");
        }

        @Test
        void padraoDeveRejeitarLocalizacaoNula() {
            assertCodigo(() -> Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID, null,
                    null, LIMIAR, T0), "CHAMADO_LOCALIZACAO_OBRIGATORIA");
        }

        @Test
        void emergenciaImediataDeveRejeitarUsuarioNulo() {
            assertCodigo(() -> Chamado.registrarEmergenciaImediata(null, LOCALIZACAO, LIMIAR, T0),
                    "CHAMADO_USUARIO_OBRIGATORIO");
        }

        @Test
        void emergenciaImediataDeveRejeitarLocalizacaoNula() {
            assertCodigo(() -> Chamado.registrarEmergenciaImediata(USUARIO_ID, null, LIMIAR, T0),
                    "CHAMADO_LOCALIZACAO_OBRIGATORIA");
        }
    }

    @Nested
    class CH2EmergenciaImediata {

        @Test
        void deveSerSemprePoliciaMilitarSemOcorrenciaESemDetalhe() {
            Chamado chamado = emergenciaImediata();

            assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.EMERGENCIA_IMEDIATA);
            assertThat(chamado.getOrgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
            assertThat(chamado.getOcorrenciaId()).isNull();
            assertThat(chamado.getDetalheAdicional()).isNull();
            assertThat(chamado.getUsuarioId()).isEqualTo(USUARIO_ID);
            assertThat(chamado.getLocalizacao()).isEqualTo(LOCALIZACAO);
            assertThat(chamado.getDataCriacao()).isEqualTo(T0);
        }

        @Test
        void deveRejeitarInstanteNulo() {
            assertCodigo(() -> Chamado.registrarEmergenciaImediata(USUARIO_ID, LOCALIZACAO, LIMIAR, null),
                    "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void factoryNaoDeveReceberOrgaoOcorrenciaNemDetalhe() throws NoSuchMethodException {
            Method factory = Chamado.class.getMethod("registrarEmergenciaImediata",
                    Long.class, Localizacao.class, Float.class, Instant.class);

            assertThat(factory.getParameterTypes())
                    .doesNotContain(OrgaoSeguranca.class, String.class)
                    .hasSize(4);
        }
    }

    @Nested
    class CH3Padrao {

        @Test
        void deveRejeitarOrgaoNulo() {
            assertCodigo(() -> Chamado.registrar(USUARIO_ID, null, OCORRENCIA_ID, LOCALIZACAO, null, LIMIAR, T0),
                    "CHAMADO_ORGAO_OBRIGATORIO");
        }

        @Test
        void deveRejeitarOcorrenciaNula() {
            assertCodigo(() -> Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, null, LOCALIZACAO, null, LIMIAR, T0),
                    "CHAMADO_OCORRENCIA_OBRIGATORIA");
        }
    }

    @Nested
    class CH4DetalheAdicional {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "   ", "\t", "\n \t "})
        void detalheAusenteOuEmBrancoDeveVirarNulo(String detalhe) {
            assertThat(padraoComDetalhe(detalhe).getDetalheAdicional()).isNull();
        }

        @Test
        void deveAparar() {
            assertThat(padraoComDetalhe("  Pessoa no 2º andar  ").getDetalheAdicional())
                    .isEqualTo("Pessoa no 2º andar");
        }

        @Test
        void deveAceitarExatamente500Caracteres() {
            String detalhe = "a".repeat(500);

            assertThat(padraoComDetalhe(detalhe).getDetalheAdicional()).isEqualTo(detalhe);
        }

        @Test
        void deveContarOLimiteDepoisDeAparar() {
            String detalhe = "a".repeat(500);

            assertThat(padraoComDetalhe("   " + detalhe + "   ").getDetalheAdicional()).isEqualTo(detalhe);
        }

        @Test
        void deveRejeitarMaisDe500Caracteres() {
            assertCodigo(() -> padraoComDetalhe("a".repeat(501)), "CHAMADO_DETALHE_EXCEDE_LIMITE");
        }
    }

    @Nested
    class CH5BaixaPrecisao {

        @Test
        void padraoComBaixaPrecisaoDeveRegistrarSinalizando() {
            Chamado chamado = Chamado.registrar(USUARIO_ID, OrgaoSeguranca.CORPO_DE_BOMBEIROS, OCORRENCIA_ID,
                    localizacaoComPrecisao(150.0f), null, LIMIAR, T0);

            assertThat(chamado.getBaixaPrecisao()).isTrue();
            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.CRIADO);
        }

        @Test
        void emergenciaImediataComBaixaPrecisaoDeveRegistrarSinalizando() {
            Chamado chamado = Chamado.registrarEmergenciaImediata(USUARIO_ID, localizacaoComPrecisao(150.0f),
                    LIMIAR, T0);

            assertThat(chamado.getBaixaPrecisao()).isTrue();
            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.CRIADO);
        }

        @ParameterizedTest
        @ValueSource(floats = {0.0f, 25.0f, 100.0f})
        void precisaoAteOLimiarNaoDeveSerBaixaPrecisao(float precisao) {
            Chamado chamado = Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID,
                    localizacaoComPrecisao(precisao), null, LIMIAR, T0);

            assertThat(chamado.getBaixaPrecisao()).isFalse();
        }

        @Test
        void deveUsarOLimiarRecebido() {
            Chamado chamado = Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID,
                    localizacaoComPrecisao(25.0f), null, 10.0f, T0);

            assertThat(chamado.getBaixaPrecisao()).isTrue();
        }

        @Test
        void deveRejeitarLimiarInvalido() {
            assertThatThrownBy(() -> Chamado.registrar(USUARIO_ID, OrgaoSeguranca.SAMU, OCORRENCIA_ID,
                    LOCALIZACAO, null, null, T0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Chamado.registrarEmergenciaImediata(USUARIO_ID, LOCALIZACAO, Float.NaN, T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CH6EstadoInicial {

        @Test
        void padraoDeveIniciarEmCriadoComPrimeiraTransicao() {
            Chamado chamado = padrao();

            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.CRIADO);
            assertThat(chamado.getTransicoes()).containsExactly(new Transicao(EstadoChamado.CRIADO, T0));
        }

        @Test
        void emergenciaImediataDeveIniciarEmCriadoComPrimeiraTransicao() {
            Chamado chamado = emergenciaImediata();

            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.CRIADO);
            assertThat(chamado.getTransicoes()).containsExactly(new Transicao(EstadoChamado.CRIADO, T0));
        }
    }

    @Nested
    class CH7MaquinaDeEstados {

        @Test
        void devePercorrerASequenciaCompleta() {
            Chamado chamado = padrao();

            chamado.encaminharSimulado(T1);
            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);

            chamado.encerrar(T2);
            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.ENCERRADO);
            assertThat(chamado.getTransicoes()).containsExactly(
                    new Transicao(EstadoChamado.CRIADO, T0),
                    new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T1),
                    new Transicao(EstadoChamado.ENCERRADO, T2));
        }

        @Test
        void emergenciaImediataDevePercorrerAMesmaSequencia() {
            Chamado chamado = emergenciaImediata();

            chamado.encaminharSimulado(T1);
            chamado.encerrar(T2);

            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.ENCERRADO);
            assertThat(chamado.getTransicoes()).hasSize(3);
        }

        @Test
        void naoDeveEncerrarAPartirDeCriado() {
            assertRejeitadaSemAlterar(padrao(), c -> c.encerrar(T1), "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void naoDeveEncaminharDuasVezes() {
            Chamado chamado = padrao();
            chamado.encaminharSimulado(T1);

            assertRejeitadaSemAlterar(chamado, c -> c.encaminharSimulado(T2), "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void naoDeveEncaminharAPartirDeEncerrado() {
            Chamado chamado = padrao();
            chamado.encaminharSimulado(T1);
            chamado.encerrar(T2);

            assertRejeitadaSemAlterar(chamado, c -> c.encaminharSimulado(T2), "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void naoDeveEncerrarDuasVezes() {
            Chamado chamado = padrao();
            chamado.encaminharSimulado(T1);
            chamado.encerrar(T2);

            assertRejeitadaSemAlterar(chamado, c -> c.encerrar(T2), "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void transicaoInvalidaDeveTerPrioridadeSobreInstanteInvalido() {
            assertRejeitadaSemAlterar(padrao(), c -> c.encerrar(null), "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void naoDeveExistirEstadoDeCancelamentoOuAtendimento() {
            assertThat(EstadoChamado.values()).containsExactly(
                    EstadoChamado.CRIADO, EstadoChamado.ENCAMINHADO_SIMULADO, EstadoChamado.ENCERRADO);
        }
    }

    @Nested
    class CH8InstantesDasTransicoes {

        @Test
        void estadoDeveCorresponderAUltimaTransicaoEmTodoOCiclo() {
            Chamado chamado = padrao();
            assertEstadoIgualAUltimaTransicao(chamado);

            chamado.encaminharSimulado(T1);
            assertEstadoIgualAUltimaTransicao(chamado);

            chamado.encerrar(T2);
            assertEstadoIgualAUltimaTransicao(chamado);
        }

        @Test
        void deveAceitarInstanteIgualAoDaUltimaTransicao() {
            Chamado chamado = padrao();

            chamado.encaminharSimulado(T0);
            chamado.encerrar(T0);

            assertThat(chamado.getTransicoes()).extracting(Transicao::getOcorridoEm).containsOnly(T0);
        }

        @Test
        void deveRejeitarEncaminhamentoComInstanteAnterior() {
            assertRejeitadaSemAlterar(padrao(), c -> c.encaminharSimulado(T0.minusMillis(1)),
                    "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarEncerramentoComInstanteAnterior() {
            Chamado chamado = padrao();
            chamado.encaminharSimulado(T1);

            assertRejeitadaSemAlterar(chamado, c -> c.encerrar(T1.minusMillis(1)), "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarEncaminhamentoComInstanteNulo() {
            assertRejeitadaSemAlterar(padrao(), c -> c.encaminharSimulado(null), "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarEncerramentoComInstanteNulo() {
            Chamado chamado = padrao();
            chamado.encaminharSimulado(T1);

            assertRejeitadaSemAlterar(chamado, c -> c.encerrar(null), "CHAMADO_INSTANTE_INVALIDO");
        }

        private void assertEstadoIgualAUltimaTransicao(Chamado chamado) {
            List<Transicao> transicoes = chamado.getTransicoes();
            assertThat(chamado.getEstado()).isEqualTo(transicoes.get(transicoes.size() - 1).getEstado());
        }
    }

    @Nested
    class CH9Imutabilidade {

        @Test
        void naoDeveExporMetodosPublicosDeAlteracao() {
            List<String> metodosPublicos = Arrays.stream(Chamado.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName)
                    .toList();

            assertThat(metodosPublicos).noneMatch(nome -> nome.startsWith("set"));
            assertThat(metodosPublicos).filteredOn(nome -> !nome.startsWith("get"))
                    .containsExactlyInAnyOrder("registrar", "registrarEmergenciaImediata", "reconstituir",
                            "encaminharSimulado", "encerrar");
        }

        @Test
        void naoDeveExporConstrutorPublico() {
            assertThat(Chamado.class.getDeclaredConstructors())
                    .extracting(Constructor::getModifiers)
                    .allMatch(Modifier::isPrivate);
        }

        @Test
        void dadosFundamentaisDevemSerCamposFinais() throws NoSuchFieldException {
            for (String campo : List.of("usuarioId", "modalidade", "orgao", "ocorrenciaId", "localizacao",
                    "baixaPrecisao", "detalheAdicional", "dataCriacao", "transicoes")) {
                assertThat(Modifier.isFinal(Chamado.class.getDeclaredField(campo).getModifiers()))
                        .as(campo).isTrue();
            }
        }

        @Test
        void listaDeTransicoesNaoDeveSerModificavel() {
            Chamado chamado = padrao();
            List<Transicao> transicoes = chamado.getTransicoes();

            assertThatThrownBy(() -> transicoes.add(new Transicao(EstadoChamado.ENCERRADO, T1)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(transicoes::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThat(chamado.getTransicoes()).hasSize(1);
        }

        @Test
        void listaObtidaAntesNaoDeveRefletirNovasTransicoes() {
            Chamado chamado = padrao();
            List<Transicao> antes = chamado.getTransicoes();

            chamado.encaminharSimulado(T1);

            assertThat(antes).hasSize(1);
            assertThat(chamado.getTransicoes()).hasSize(2);
        }

        @Test
        void dadosFundamentaisNaoDevemMudarComAsTransicoes() {
            Chamado chamado = Chamado.registrar(USUARIO_ID, OrgaoSeguranca.POLICIA_MILITAR, OCORRENCIA_ID,
                    LOCALIZACAO, "Detalhe", LIMIAR, T0);

            chamado.encaminharSimulado(T1);
            chamado.encerrar(T2);

            assertThat(chamado.getOrgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
            assertThat(chamado.getOcorrenciaId()).isEqualTo(OCORRENCIA_ID);
            assertThat(chamado.getLocalizacao()).isEqualTo(LOCALIZACAO);
            assertThat(chamado.getDetalheAdicional()).isEqualTo("Detalhe");
            assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
            assertThat(chamado.getBaixaPrecisao()).isFalse();
            assertThat(chamado.getDataCriacao()).isEqualTo(T0);
        }
    }

    @Nested
    class Reconstituicao {

        private static final Long ID = 42L;
        private static final Transicao CRIADO_T0 = new Transicao(EstadoChamado.CRIADO, T0);
        private static final Transicao ENCAMINHADO_T1 = new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T1);
        private static final Transicao ENCERRADO_T2 = new Transicao(EstadoChamado.ENCERRADO, T2);

        private Chamado padrao(EstadoChamado estado, List<Transicao> transicoes) {
            return Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU, OCORRENCIA_ID,
                    LOCALIZACAO, false, "Detalhe", estado, T0, transicoes);
        }

        @Test
        void devePreservarTodosOsCamposDoPadrao() {
            Chamado chamado = padrao(EstadoChamado.ENCAMINHADO_SIMULADO, List.of(CRIADO_T0, ENCAMINHADO_T1));

            assertThat(chamado.getId()).isEqualTo(ID);
            assertThat(chamado.getUsuarioId()).isEqualTo(USUARIO_ID);
            assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.PADRAO);
            assertThat(chamado.getOrgao()).isEqualTo(OrgaoSeguranca.SAMU);
            assertThat(chamado.getOcorrenciaId()).isEqualTo(OCORRENCIA_ID);
            assertThat(chamado.getLocalizacao()).isEqualTo(LOCALIZACAO);
            assertThat(chamado.getBaixaPrecisao()).isFalse();
            assertThat(chamado.getDetalheAdicional()).isEqualTo("Detalhe");
            assertThat(chamado.getEstado()).isEqualTo(EstadoChamado.ENCAMINHADO_SIMULADO);
            assertThat(chamado.getDataCriacao()).isEqualTo(T0);
            assertThat(chamado.getTransicoes()).containsExactly(CRIADO_T0, ENCAMINHADO_T1);
        }

        @Test
        void devePreservarEmergenciaImediata() {
            Chamado chamado = Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.EMERGENCIA_IMEDIATA,
                    OrgaoSeguranca.POLICIA_MILITAR, null, LOCALIZACAO, true, null, EstadoChamado.CRIADO, T0,
                    List.of(CRIADO_T0));

            assertThat(chamado.getModalidade()).isEqualTo(ModalidadeChamado.EMERGENCIA_IMEDIATA);
            assertThat(chamado.getOrgao()).isEqualTo(OrgaoSeguranca.POLICIA_MILITAR);
            assertThat(chamado.getOcorrenciaId()).isNull();
            assertThat(chamado.getDetalheAdicional()).isNull();
            assertThat(chamado.getBaixaPrecisao()).isTrue();
        }

        // N2: o valor gravado prevalece, mesmo que o limiar atual levasse a outro resultado.
        @Test
        void devePreservarBaixaPrecisaoGravadoSemRecalcular() {
            Chamado chamado = Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, localizacaoComPrecisao(10.0f), true, null, EstadoChamado.CRIADO, T0,
                    List.of(CRIADO_T0));

            assertThat(chamado.getBaixaPrecisao()).isTrue();
        }

        @Test
        void devePreservarOsTresEstadosPossiveis() {
            assertThat(padrao(EstadoChamado.CRIADO, List.of(CRIADO_T0)).getTransicoes()).hasSize(1);
            assertThat(padrao(EstadoChamado.ENCAMINHADO_SIMULADO, List.of(CRIADO_T0, ENCAMINHADO_T1))
                    .getTransicoes()).hasSize(2);
            assertThat(padrao(EstadoChamado.ENCERRADO, List.of(CRIADO_T0, ENCAMINHADO_T1, ENCERRADO_T2))
                    .getEstado()).isEqualTo(EstadoChamado.ENCERRADO);
        }

        @Test
        void deveContinuarProtegendoAMaquinaDeEstados() {
            Chamado criado = padrao(EstadoChamado.CRIADO, List.of(CRIADO_T0));
            assertRejeitadaSemAlterar(criado, c -> c.encerrar(T1), "CHAMADO_TRANSICAO_INVALIDA");

            Chamado encaminhado = padrao(EstadoChamado.ENCAMINHADO_SIMULADO, List.of(CRIADO_T0, ENCAMINHADO_T1));
            assertRejeitadaSemAlterar(encaminhado, c -> c.encerrar(T0), "CHAMADO_INSTANTE_INVALIDO");
            encaminhado.encerrar(T2);
            assertThat(encaminhado.getTransicoes()).containsExactly(CRIADO_T0, ENCAMINHADO_T1, ENCERRADO_T2);
        }

        @Test
        void naoDeveCompartilharAListaRecebida() {
            List<Transicao> transicoes = new java.util.ArrayList<>(List.of(CRIADO_T0));
            Chamado chamado = padrao(EstadoChamado.CRIADO, transicoes);

            transicoes.add(ENCAMINHADO_T1);

            assertThat(chamado.getTransicoes()).containsExactly(CRIADO_T0);
        }

        @Test
        void deveApararODetalhe() {
            Chamado chamado = Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, LOCALIZACAO, false, "  Detalhe  ", EstadoChamado.CRIADO, T0, List.of(CRIADO_T0));

            assertThat(chamado.getDetalheAdicional()).isEqualTo("Detalhe");
        }

        @Test
        void deveRejeitarNulosEstruturais() {
            List<Runnable> casos = List.of(
                    () -> Chamado.reconstituir(null, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                            OCORRENCIA_ID, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    () -> Chamado.reconstituir(ID, USUARIO_ID, null, OrgaoSeguranca.SAMU,
                            OCORRENCIA_ID, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    () -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                            OCORRENCIA_ID, LOCALIZACAO, null, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    () -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                            OCORRENCIA_ID, LOCALIZACAO, false, null, null, T0, List.of(CRIADO_T0)),
                    () -> padrao(EstadoChamado.CRIADO, null),
                    () -> padrao(EstadoChamado.CRIADO, List.of()),
                    () -> padrao(EstadoChamado.CRIADO, java.util.Arrays.asList(CRIADO_T0, null)));

            assertThat(casos).allSatisfy(caso ->
                    assertThatThrownBy(caso::run).isInstanceOf(IllegalArgumentException.class));
        }

        @Test
        void deveAplicarCH1() {
            assertCodigo(() -> Chamado.reconstituir(ID, null, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    "CHAMADO_USUARIO_OBRIGATORIO");
            assertCodigo(() -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, null, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    "CHAMADO_LOCALIZACAO_OBRIGATORIA");
        }

        @Test
        void deveRejeitarDataDeCriacaoNula() {
            assertCodigo(() -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, LOCALIZACAO, false, null, EstadoChamado.CRIADO, null, List.of(CRIADO_T0)),
                    "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarEmergenciaImediataIncoerente() {
            List<Runnable> casos = List.of(
                    () -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.EMERGENCIA_IMEDIATA,
                            OrgaoSeguranca.SAMU, null, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0,
                            List.of(CRIADO_T0)),
                    () -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.EMERGENCIA_IMEDIATA,
                            OrgaoSeguranca.POLICIA_MILITAR, OCORRENCIA_ID, LOCALIZACAO, false, null,
                            EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    () -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.EMERGENCIA_IMEDIATA,
                            OrgaoSeguranca.POLICIA_MILITAR, null, LOCALIZACAO, false, "Detalhe",
                            EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)));

            assertThat(casos).allSatisfy(caso ->
                    assertThatThrownBy(caso::run).isInstanceOf(IllegalArgumentException.class));
        }

        @Test
        void deveAplicarCH3() {
            assertCodigo(() -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, null,
                    OCORRENCIA_ID, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    "CHAMADO_ORGAO_OBRIGATORIO");
            assertCodigo(() -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    null, LOCALIZACAO, false, null, EstadoChamado.CRIADO, T0, List.of(CRIADO_T0)),
                    "CHAMADO_OCORRENCIA_OBRIGATORIA");
        }

        @Test
        void deveAplicarCH4() {
            assertCodigo(() -> Chamado.reconstituir(ID, USUARIO_ID, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU,
                    OCORRENCIA_ID, LOCALIZACAO, false, "a".repeat(501), EstadoChamado.CRIADO, T0,
                    List.of(CRIADO_T0)), "CHAMADO_DETALHE_EXCEDE_LIMITE");
        }

        @Test
        void deveRejeitarPrimeiraTransicaoDiferenteDeCriadoNaCriacao() {
            assertCodigo(() -> padrao(EstadoChamado.ENCAMINHADO_SIMULADO, List.of(ENCAMINHADO_T1)),
                    "CHAMADO_TRANSICAO_INVALIDA");
            assertCodigo(() -> padrao(EstadoChamado.CRIADO, List.of(new Transicao(EstadoChamado.CRIADO, T1))),
                    "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void deveRejeitarSequenciaForaDoCicloDeVida() {
            assertCodigo(() -> padrao(EstadoChamado.ENCERRADO, List.of(CRIADO_T0, ENCERRADO_T2)),
                    "CHAMADO_TRANSICAO_INVALIDA");
            assertCodigo(() -> padrao(EstadoChamado.CRIADO,
                    List.of(CRIADO_T0, ENCAMINHADO_T1, new Transicao(EstadoChamado.CRIADO, T2))),
                    "CHAMADO_TRANSICAO_INVALIDA");
            assertCodigo(() -> padrao(EstadoChamado.ENCERRADO,
                    List.of(CRIADO_T0, ENCAMINHADO_T1, ENCERRADO_T2, ENCERRADO_T2)),
                    "CHAMADO_TRANSICAO_INVALIDA");
        }

        @Test
        void deveRejeitarInstantesDecrescentes() {
            assertCodigo(() -> padrao(EstadoChamado.ENCAMINHADO_SIMULADO,
                    List.of(CRIADO_T0, new Transicao(EstadoChamado.ENCAMINHADO_SIMULADO, T0.minusMillis(1)))),
                    "CHAMADO_INSTANTE_INVALIDO");
        }

        @Test
        void deveRejeitarEstadoDiferenteDaUltimaTransicao() {
            assertCodigo(() -> padrao(EstadoChamado.ENCERRADO, List.of(CRIADO_T0, ENCAMINHADO_T1)),
                    "CHAMADO_TRANSICAO_INVALIDA");
        }
    }
}
