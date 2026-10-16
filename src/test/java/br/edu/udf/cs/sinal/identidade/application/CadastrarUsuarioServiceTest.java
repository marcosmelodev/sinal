package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CadastrarUsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-16T12:00:00.123456Z");
    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA = "senha-forte-123";
    private static final String HASH = "$2a$12$hash";

    private UsuarioRepository repository;
    private PasswordEncoder encoder;
    private CadastrarUsuarioService service;

    @BeforeEach
    void configurar() {
        repository = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new CadastrarUsuarioService(repository, encoder, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(encoder.encode(anyString())).thenReturn(HASH);
        when(repository.buscarPorCpf(anyString())).thenReturn(Optional.empty());
        when(repository.buscarPorEmail(anyString())).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(invocacao -> {
            Usuario u = invocacao.getArgument(0);
            return Usuario.reconstituir(1L, u.getNome(), u.getCpf(), u.getEmail(), u.getSenhaHash(),
                    u.getDataCadastro(), u.getAtivo());
        });
    }

    private static CadastrarUsuarioCommand comando(String cpf, String email, String senha) {
        return new CadastrarUsuarioCommand("Maria da Silva", cpf, email, senha);
    }

    private static Usuario existente() {
        return Usuario.reconstituir(9L, "Outra", CPF, EMAIL, HASH, AGORA, true);
    }

    private static void assertAplicacao(Runnable operacao, String codigo, String... campos) {
        assertThatThrownBy(operacao::run)
                .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo(codigo);
                    assertThat(e.getCampos()).containsExactlyInAnyOrder(campos);
                });
    }

    private static DataIntegrityViolationException violacao(String mensagemDoBanco) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(mensagemDoBanco));
    }

    @Nested
    class Sucesso {

        @Test
        void deveCadastrarComHashInstanteDoClockEAtivo() {
            UsuarioCadastrado resultado = service.executar(comando(CPF, EMAIL, SENHA));

            verify(encoder).encode(SENHA);
            ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).salvar(salvo.capture());
            assertThat(salvo.getValue().getId()).isNull();
            assertThat(salvo.getValue().getSenhaHash()).isEqualTo(HASH);
            assertThat(salvo.getValue().getDataCadastro()).isEqualTo(AGORA);
            assertThat(salvo.getValue().getAtivo()).isTrue();

            assertThat(resultado.id()).isEqualTo(1L);
            assertThat(resultado.nome()).isEqualTo("Maria da Silva");
            assertThat(resultado.cpfMascarado()).isEqualTo("***.982.247-**");
            assertThat(resultado.email()).isEqualTo(EMAIL);
            assertThat(resultado.dataCadastro()).isEqualTo(AGORA);
            assertThat(resultado.ativo()).isTrue();
            assertThat(resultado.toString()).doesNotContain(HASH).doesNotContain(CPF);
        }

        @Test
        void deveConsultarEGravarCpfEEmailNormalizados() {
            service.executar(comando("529.982.247-25", "  Maria@Exemplo.COM.br ", SENHA));

            verify(repository).buscarPorCpf(CPF);
            verify(repository).buscarPorEmail(EMAIL);
            ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
            verify(repository).salvar(salvo.capture());
            assertThat(salvo.getValue().getCpf()).isEqualTo(CPF);
            assertThat(salvo.getValue().getEmail()).isEqualTo(EMAIL);
        }
    }

    @Nested
    class Unicidade {

        @Test
        void cpfDuplicadoDeveSerRejeitadoSemSalvar() {
            when(repository.buscarPorCpf(CPF)).thenReturn(Optional.of(existente()));

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)), "CADASTRO_JA_EXISTENTE", "cpf");
            verify(repository, never()).salvar(any());
        }

        @Test
        void emailDuplicadoDeveSerRejeitadoSemSalvar() {
            when(repository.buscarPorEmail(EMAIL)).thenReturn(Optional.of(existente()));

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)), "CADASTRO_JA_EXISTENTE", "email");
            verify(repository, never()).salvar(any());
        }

        @Test
        void cpfEEmailDuplicadosDevemSerInformadosJuntos() {
            when(repository.buscarPorCpf(CPF)).thenReturn(Optional.of(existente()));
            when(repository.buscarPorEmail(EMAIL)).thenReturn(Optional.of(existente()));

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)),
                    "CADASTRO_JA_EXISTENTE", "cpf", "email");
        }

        @Test
        void hashDeveSerCalculadoMesmoQuandoODadoJaExiste() {
            when(repository.buscarPorCpf(CPF)).thenReturn(Optional.of(existente()));

            assertThatThrownBy(() -> service.executar(comando(CPF, EMAIL, SENHA)))
                    .isInstanceOf(AplicacaoException.class);
            verify(encoder).encode(SENHA);
        }
    }

    @Nested
    class CondicaoDeCorrida {

        @Test
        void violacaoDoCpfDeveVirarCadastroJaExistenteNoCampoCpf() {
            doThrow(
                    violacao("Duplicate entry '52998224725' for key 'usuario.uk_usuario_cpf'")).when(repository).salvar(any());

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)), "CADASTRO_JA_EXISTENTE", "cpf");
        }

        @Test
        void violacaoDoEmailDeveVirarCadastroJaExistenteNoCampoEmail() {
            doThrow(
                    violacao("Duplicate entry 'maria@exemplo.com.br' for key 'usuario.uk_usuario_email'")).when(repository).salvar(any());

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)), "CADASTRO_JA_EXISTENTE", "email");
        }

        @Test
        void duplicidadeEmRestricaoDesconhecidaDeveVirarCadastroJaExistenteSemCampo() {
            doThrow(violacao("Duplicate entry 'x' for key 'usuario.outra'")).when(repository).salvar(any());

            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, SENHA)), "CADASTRO_JA_EXISTENTE");
        }

        @Test
        void violacaoQueNaoEDuplicidadeNaoDeveSerTraduzida() {
            DataIntegrityViolationException original = violacao("Data too long for column 'nome' at row 1");
            doThrow(original).when(repository).salvar(any());

            assertThatThrownBy(() -> service.executar(comando(CPF, EMAIL, SENHA))).isSameAs(original);
        }
    }

    @Nested
    class ValidacaoDeDominio {

        @Test
        void cpfInvalidoDeveSerRejeitadoPeloDominioSemConsultarORepositorio() {
            assertThatThrownBy(() -> service.executar(comando("52998224724", EMAIL, SENHA)))
                    .isInstanceOf(DominioException.class)
                    .extracting(e -> ((DominioException) e).getCodigo())
                    .isEqualTo("USUARIO_CPF_INVALIDO");
            verifyNoInteractions(repository);
        }

        @Test
        void emailInvalidoDeveSerRejeitadoPeloDominio() {
            assertThatThrownBy(() -> service.executar(comando(CPF, "sem-arroba", SENHA)))
                    .isInstanceOf(DominioException.class)
                    .extracting(e -> ((DominioException) e).getCodigo())
                    .isEqualTo("USUARIO_EMAIL_INVALIDO");
            verifyNoInteractions(repository);
        }
    }

    @Nested
    class PoliticaDeSenha {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"        ", "1234567", "abcdefg"})
        void senhaAusenteOuCurtaDeveSerRejeitadaSemCalcularHash(String senha) {
            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, senha)), "SENHA_INVALIDA", "senha");
            verifyNoInteractions(encoder, repository);
        }

        @Test
        void senhaAcimaDe72BytesDeveSerRejeitada() {
            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, "a".repeat(73))), "SENHA_INVALIDA", "senha");
            verifyNoInteractions(encoder);
        }

        // "ç" ocupa 2 bytes em UTF-8: 37 caracteres somam 74 bytes.
        @Test
        void limiteDeveSerMedidoEmBytesUtf8() {
            assertAplicacao(() -> service.executar(comando(CPF, EMAIL, "ç".repeat(37))), "SENHA_INVALIDA", "senha");
            verifyNoInteractions(encoder);
        }

        @Test
        void limitesExatosDevemSerAceitos() {
            service.executar(comando(CPF, EMAIL, "12345678"));
            verify(encoder).encode("12345678");

            service.executar(comando(CPF, EMAIL, "a".repeat(72)));
            verify(encoder).encode("a".repeat(72));
        }

        @Test
        void senhaNaoDeveSerAparada() {
            service.executar(comando(CPF, EMAIL, "  senha123  "));

            verify(encoder).encode("  senha123  ");
        }

        @Test
        void minimoDeveContarCaracteresNaoBytes() {
            service.executar(comando(CPF, EMAIL, "çççççççç"));

            verify(encoder).encode("çççççççç");
        }
    }

    @Test
    void toStringDoComandoNaoDeveExporSenhaNemCpf() {
        String texto = comando(CPF, EMAIL, SENHA).toString();

        assertThat(texto).doesNotContain(SENHA).doesNotContain(CPF).contains("senha=***").contains("cpf=***");
    }
}
