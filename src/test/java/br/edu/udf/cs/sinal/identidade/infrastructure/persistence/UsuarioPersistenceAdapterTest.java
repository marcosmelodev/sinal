package br.edu.udf.cs.sinal.identidade.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UsuarioPersistenceAdapter.class})
class UsuarioPersistenceAdapterTest {

    private static final String NOME = "Maria da Silva";
    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z4u3hqU0o4xT8bQ0ZjzPZ9mC";
    private static final Instant INSTANTE = Instant.parse("2026-10-15T12:34:56.123456Z");

    @Autowired
    private UsuarioPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private Usuario novoUsuario(String cpf, String email) {
        return Usuario.cadastrar(NOME, cpf, email, SENHA_HASH, INSTANTE);
    }

    // Força a ida ao banco: grava o pendente e descarta o cache de primeiro nível.
    private void sincronizarComOBanco() {
        entityManager.flush();
        entityManager.clear();
    }

    private static void assertMesmosDados(Usuario lido, Usuario esperado) {
        assertThat(lido.getId()).isEqualTo(esperado.getId());
        assertThat(lido.getNome()).isEqualTo(esperado.getNome());
        assertThat(lido.getCpf()).isEqualTo(esperado.getCpf());
        assertThat(lido.getEmail()).isEqualTo(esperado.getEmail());
        assertThat(lido.getSenhaHash()).isEqualTo(esperado.getSenhaHash());
        assertThat(lido.getDataCadastro()).isEqualTo(esperado.getDataCadastro());
        assertThat(lido.getAtivo()).isEqualTo(esperado.getAtivo());
    }

    @Test
    void salvarDeveGerarIdEPreservarOsDados() {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));

        assertThat(salvo.getId()).isNotNull().isPositive();
        assertThat(salvo.getNome()).isEqualTo(NOME);
        assertThat(salvo.getCpf()).isEqualTo(CPF);
        assertThat(salvo.getEmail()).isEqualTo(EMAIL);
        assertThat(salvo.getSenhaHash()).isEqualTo(SENHA_HASH);
        assertThat(salvo.getDataCadastro()).isEqualTo(INSTANTE);
        assertThat(salvo.getAtivo()).isTrue();
    }

    @Test
    void deveRecuperarPorId() {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(salvo.getId())).hasValueSatisfying(lido -> assertMesmosDados(lido, salvo));
    }

    @Test
    void deveRecuperarPorCpf() {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorCpf(CPF)).hasValueSatisfying(lido -> assertMesmosDados(lido, salvo));
    }

    @Test
    void deveRecuperarPorEmail() {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorEmail(EMAIL)).hasValueSatisfying(lido -> assertMesmosDados(lido, salvo));
    }

    @Test
    void buscasSemResultadoDevemSerVazias() {
        adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(999_999L)).isEmpty();
        assertThat(adapter.buscarPorCpf("11144477735")).isEmpty();
        assertThat(adapter.buscarPorEmail("outro@exemplo.com")).isEmpty();
    }

    @Test
    void buscaPorCpfDeveExigirValorCanonico() {
        adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorCpf("529.982.247-25")).isEmpty();
    }

    @Test
    void deveGravarCpfEEmailNormalizadosPeloDominio() {
        Usuario salvo = adapter.salvar(novoUsuario("529.982.247-25", "  Maria@Exemplo.COM.br "));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(salvo.getId())).hasValueSatisfying(lido -> {
            assertThat(lido.getCpf()).isEqualTo(CPF);
            assertThat(lido.getEmail()).isEqualTo(EMAIL);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void devePreservarOIndicadorAtivo(boolean ativo) {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));
        adapter.salvar(Usuario.reconstituir(salvo.getId(), NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, ativo));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(salvo.getId())).hasValueSatisfying(lido ->
                assertThat(lido.getAtivo()).isEqualTo(ativo));
    }

    @Test
    void devePreservarMicrossegundosEmUtc() {
        Usuario salvo = adapter.salvar(novoUsuario(CPF, EMAIL));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(salvo.getId())).hasValueSatisfying(lido ->
                assertThat(lido.getDataCadastro()).isEqualTo(Instant.parse("2026-10-15T12:34:56.123456Z")));
    }

    // DATETIME(6) guarda microssegundos e o MySQL ARREDONDA a fração excedente (não trunca),
    // pois TIME_TRUNCATE_FRACTIONAL não está no sql_mode padrão. Instantes já em microssegundos não mudam.
    @Test
    void deveArredondarNanossegundosParaMicrossegundos() {
        Instant comNanossegundos = Instant.parse("2026-10-15T12:34:56.123456789Z");
        Usuario salvo = adapter.salvar(Usuario.cadastrar(NOME, CPF, EMAIL, SENHA_HASH, comNanossegundos));
        sincronizarComOBanco();

        assertThat(adapter.buscarPorId(salvo.getId())).hasValueSatisfying(lido -> {
            assertThat(lido.getDataCadastro()).isNotEqualTo(comNanossegundos);
            assertThat(lido.getDataCadastro()).isNotEqualTo(comNanossegundos.truncatedTo(ChronoUnit.MICROS));
            assertThat(lido.getDataCadastro()).isEqualTo(Instant.parse("2026-10-15T12:34:56.123457Z"));
        });
    }

    @Test
    void deveRejeitarCpfDuplicado() {
        adapter.salvar(novoUsuario(CPF, EMAIL));

        assertThatThrownBy(() -> {
            adapter.salvar(novoUsuario(CPF, "outro@exemplo.com"));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarEmailDuplicado() {
        adapter.salvar(novoUsuario(CPF, EMAIL));

        assertThatThrownBy(() -> {
            adapter.salvar(novoUsuario("11144477735", EMAIL));
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
