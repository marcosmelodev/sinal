package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

// Contexto completo com MySQL real e BCrypt real; cada teste desfaz a própria transação.
// O repositório é um spy apenas para simular a corrida; nos demais testes ele se comporta como o real.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CadastrarUsuarioServiceIntegrationTest {

    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA = "senha-forte-123";

    @Autowired
    private CadastrarUsuarioService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;

    private static CadastrarUsuarioCommand comando(String cpf, String email) {
        return new CadastrarUsuarioCommand("Maria da Silva", cpf, email, SENHA);
    }

    @Test
    void deveCadastrarDePontaAPontaComHashBcryptEDadosNormalizados() {
        UsuarioCadastrado resultado = service.executar(comando("529.982.247-25", "  Maria@Exemplo.COM.br "));

        assertThat(resultado.id()).isNotNull();
        assertThat(resultado.cpfMascarado()).isEqualTo("***.982.247-**");
        assertThat(resultado.email()).isEqualTo(EMAIL);
        assertThat(resultado.ativo()).isTrue();
        assertThat(resultado.dataCadastro().getNano() % 1_000).isZero();

        Usuario gravado = usuarioRepository.buscarPorCpf(CPF).orElseThrow();
        assertThat(gravado.getId()).isEqualTo(resultado.id());
        assertThat(gravado.getEmail()).isEqualTo(EMAIL);
        assertThat(gravado.getSenhaHash()).startsWith("$2a$12$").isNotEqualTo(SENHA);
        assertThat(passwordEncoder.matches(SENHA, gravado.getSenhaHash())).isTrue();
        assertThat(gravado.getDataCadastro()).isEqualTo(resultado.dataCadastro());
    }

    @Test
    void cpfJaCadastradoDeveSerRejeitado() {
        service.executar(comando(CPF, EMAIL));

        assertThatThrownBy(() -> service.executar(comando(CPF, "outro@exemplo.com")))
                .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CADASTRO_JA_EXISTENTE");
                    assertThat(e.getCampos()).containsExactly("cpf");
                });
    }

    @Test
    void emailJaCadastradoDeveSerRejeitadoMesmoComOutraGrafia() {
        service.executar(comando(CPF, EMAIL));

        assertThatThrownBy(() -> service.executar(comando("11144477735", "MARIA@exemplo.com.br")))
                .isInstanceOfSatisfying(AplicacaoException.class, e ->
                        assertThat(e.getCampos()).containsExactly("email"));
    }

    // Corrida: a checagem prévia não enxerga o outro cadastro; o UNIQUE do MySQL decide e o serviço
    // traduz pelo nome real da restrição informado pelo banco.
    @Test
    void corridaNoCpfDeveSerTraduzidaPelaRestricaoDoBanco() {
        service.executar(comando(CPF, EMAIL));
        doReturn(Optional.empty()).when(usuarioRepository).buscarPorCpf(anyString());
        doReturn(Optional.empty()).when(usuarioRepository).buscarPorEmail(anyString());

        assertThatThrownBy(() -> service.executar(comando(CPF, "outro@exemplo.com")))
                .isInstanceOfSatisfying(AplicacaoException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CADASTRO_JA_EXISTENTE");
                    assertThat(e.getCampos()).containsExactly("cpf");
                });
    }

    @Test
    void corridaNoEmailDeveSerTraduzidaPelaRestricaoDoBanco() {
        service.executar(comando(CPF, EMAIL));
        doReturn(Optional.empty()).when(usuarioRepository).buscarPorCpf(anyString());
        doReturn(Optional.empty()).when(usuarioRepository).buscarPorEmail(anyString());

        assertThatThrownBy(() -> service.executar(comando("11144477735", EMAIL)))
                .isInstanceOfSatisfying(AplicacaoException.class, e ->
                        assertThat(e.getCampos()).containsExactly("email"));
    }

    @Test
    void senhaForaDaPoliticaNaoDeveGravarNada() {
        assertThatThrownBy(() -> service.executar(new CadastrarUsuarioCommand("Maria", CPF, EMAIL, "curta")))
                .isInstanceOfSatisfying(AplicacaoException.class, e ->
                        assertThat(e.getCodigo()).isEqualTo("SENHA_INVALIDA"));

        assertThat(usuarioRepository.buscarPorCpf(CPF)).isEmpty();
    }
}
