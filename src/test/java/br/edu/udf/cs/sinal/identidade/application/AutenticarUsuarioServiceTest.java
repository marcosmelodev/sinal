package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutenticarUsuarioServiceTest {

    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA = "senha-forte-123";
    private static final String HASH = "$2a$12$hash-real";
    private static final String HASH_FICTICIO = "$2a$12$hash-ficticio";
    private static final Instant AGORA = Instant.parse("2026-10-17T12:00:00Z");
    private static final TokenEmitido TOKEN = new TokenEmitido("token", AGORA.plusSeconds(900));

    private UsuarioRepository repository;
    private PasswordEncoder encoder;
    private EmissorDeToken emissor;
    private AutenticarUsuarioService service;

    @BeforeEach
    void configurar() {
        repository = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        emissor = mock(EmissorDeToken.class);
        when(encoder.encode(anyString())).thenReturn(HASH_FICTICIO);
        when(encoder.matches(SENHA, HASH)).thenReturn(true);
        when(repository.buscarPorCpf(anyString())).thenReturn(Optional.empty());
        when(repository.buscarPorEmail(anyString())).thenReturn(Optional.empty());
        when(emissor.emitir(any())).thenReturn(TOKEN);
        service = new AutenticarUsuarioService(repository, encoder, emissor);
    }

    private void existe(boolean ativo) {
        Usuario usuario = Usuario.reconstituir(42L, "Maria", CPF, EMAIL, HASH, AGORA, ativo);
        when(repository.buscarPorCpf(CPF)).thenReturn(Optional.of(usuario));
        when(repository.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario));
    }

    // Todos os caminhos de falha respondem exatamente o mesmo erro.
    private void assertCredenciaisInvalidas(String identificador, String senha) {
        assertThatThrownBy(() -> service.executar(identificador, senha))
                .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CREDENCIAIS_INVALIDAS");
                    assertThat(e.getMensagem()).isEqualTo("Dados incorretos.");
                    assertThat(e.getCampos()).isEmpty();
                });
        verify(emissor, never()).emitir(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {CPF, "529.982.247-25", " 529.982.247-25 "})
    void deveAutenticarPorCpfComOuSemMascara(String identificador) {
        existe(true);

        assertThat(service.executar(identificador, SENHA)).isEqualTo(TOKEN);
        verify(repository).buscarPorCpf(CPF);
        verify(emissor).emitir(42L);
    }

    @Test
    void deveAutenticarPorEmailNormalizado() {
        existe(true);

        assertThat(service.executar("  Maria@Exemplo.COM.br ", SENHA)).isEqualTo(TOKEN);
        verify(repository).buscarPorEmail(EMAIL);
        verify(repository, never()).buscarPorCpf(anyString());
    }

    @Test
    void senhaErradaDeveSerCredencialInvalida() {
        existe(true);

        assertCredenciaisInvalidas(CPF, "senha-errada");
        verify(encoder).matches("senha-errada", HASH);
    }

    @Test
    void usuarioInexistenteDeveSerCredencialInvalidaComBcryptContraHashFicticio() {
        assertCredenciaisInvalidas(CPF, SENHA);
        verify(encoder).matches(SENHA, HASH_FICTICIO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "52998224724", "11111111111", "a@b", "nao é cpf"})
    void identificadorInvalidoDeveSerCredencialInvalidaSemConsultarEComBcrypt(String identificador) {
        assertCredenciaisInvalidas(identificador, SENHA);
        verify(encoder).matches(SENHA, HASH_FICTICIO);
        verify(repository, never()).buscarPorCpf(anyString());
        verify(repository, never()).buscarPorEmail(anyString());
    }

    @Test
    void senhaAusenteOuLongaDemaisDeveSerCredencialInvalidaSemErroEComBcrypt() {
        existe(true);

        assertCredenciaisInvalidas(CPF, null);
        assertCredenciaisInvalidas(CPF, "");
        assertCredenciaisInvalidas(CPF, "a".repeat(73));
        verify(encoder, org.mockito.Mockito.times(3)).matches("", HASH);
    }

    @Test
    void contaInativaComSenhaCorretaDeveSerContaInativa() {
        existe(false);

        assertThatThrownBy(() -> service.executar(CPF, SENHA))
                .isInstanceOfSatisfying(AplicacaoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("CONTA_INATIVA"));
        verifyNoInteractions(emissor);
    }

    @Test
    void contaInativaComSenhaErradaNaoDeveRevelarOStatus() {
        existe(false);

        assertCredenciaisInvalidas(CPF, "senha-errada");
    }
}
