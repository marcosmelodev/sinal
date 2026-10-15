package br.edu.udf.cs.sinal.identidade.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

    private static final String NOME = "Maria da Silva";
    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z4u3hqU0o4xT8bQ0ZjzPZ9mC";
    private static final Instant INSTANTE = Instant.parse("2026-10-15T12:00:00Z");

    private static Usuario comNome(String nome) {
        return Usuario.cadastrar(nome, CPF, EMAIL, SENHA_HASH, INSTANTE);
    }

    private static Usuario comCpf(String cpf) {
        return Usuario.cadastrar(NOME, cpf, EMAIL, SENHA_HASH, INSTANTE);
    }

    private static Usuario comEmail(String email) {
        return Usuario.cadastrar(NOME, CPF, email, SENHA_HASH, INSTANTE);
    }

    private static Usuario comSenhaHash(String senhaHash) {
        return Usuario.cadastrar(NOME, CPF, EMAIL, senhaHash, INSTANTE);
    }

    private static void assertCodigo(Runnable criacao, String codigo) {
        assertThatThrownBy(criacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Nested
    class Cadastro {

        @Test
        void deveCadastrarComTodosOsCamposPreenchidos() {
            Usuario usuario = Usuario.cadastrar(NOME, CPF, EMAIL, SENHA_HASH, INSTANTE);

            assertThat(usuario.getId()).isNull();
            assertThat(usuario.getNome()).isEqualTo(NOME);
            assertThat(usuario.getCpf()).isEqualTo(CPF);
            assertThat(usuario.getEmail()).isEqualTo(EMAIL);
            assertThat(usuario.getSenhaHash()).isEqualTo(SENHA_HASH);
            assertThat(usuario.getDataCadastro()).isEqualTo(INSTANTE);
            assertThat(usuario.getAtivo()).isTrue();
        }

        @Test
        void deveRejeitarDataCadastroNula() {
            assertCodigo(() -> Usuario.cadastrar(NOME, CPF, EMAIL, SENHA_HASH, null),
                    "USUARIO_DATA_CADASTRO_OBRIGATORIA");
        }
    }

    @Nested
    class Reconstituicao {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void devePreservarTodosOsCampos(boolean ativo) {
            Usuario usuario = Usuario.reconstituir(42L, NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, ativo);

            assertThat(usuario.getId()).isEqualTo(42L);
            assertThat(usuario.getNome()).isEqualTo(NOME);
            assertThat(usuario.getCpf()).isEqualTo(CPF);
            assertThat(usuario.getEmail()).isEqualTo(EMAIL);
            assertThat(usuario.getSenhaHash()).isEqualTo(SENHA_HASH);
            assertThat(usuario.getDataCadastro()).isEqualTo(INSTANTE);
            assertThat(usuario.getAtivo()).isEqualTo(ativo);
        }

        @Test
        void deveRejeitarIdNulo() {
            assertThatThrownBy(() -> Usuario.reconstituir(null, NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarAtivoNulo() {
            assertThatThrownBy(() -> Usuario.reconstituir(1L, NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveAplicarAsMesmasInvariantesDoCadastro() {
            assertCodigo(() -> Usuario.reconstituir(1L, " ", CPF, EMAIL, SENHA_HASH, INSTANTE, true),
                    "USUARIO_NOME_OBRIGATORIO");
            assertCodigo(() -> Usuario.reconstituir(1L, NOME, "52998224724", EMAIL, SENHA_HASH, INSTANTE, true),
                    "USUARIO_CPF_INVALIDO");
            assertCodigo(() -> Usuario.reconstituir(1L, NOME, CPF, "invalido", SENHA_HASH, INSTANTE, true),
                    "USUARIO_EMAIL_INVALIDO");
            assertCodigo(() -> Usuario.reconstituir(1L, NOME, CPF, EMAIL, "", INSTANTE, true),
                    "USUARIO_SENHA_OBRIGATORIA");
            assertCodigo(() -> Usuario.reconstituir(1L, NOME, CPF, EMAIL, SENHA_HASH, null, true),
                    "USUARIO_DATA_CADASTRO_OBRIGATORIA");
        }

        @Test
        void deveNormalizarCpfEEmail() {
            Usuario usuario = Usuario.reconstituir(1L, NOME, "529.982.247-25", " Maria@Exemplo.COM.br ",
                    SENHA_HASH, INSTANTE, true);

            assertThat(usuario.getCpf()).isEqualTo(CPF);
            assertThat(usuario.getEmail()).isEqualTo(EMAIL);
        }
    }

    @Nested
    class Nome {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t\n"})
        void deveRejeitarNomeAusenteOuEmBranco(String nome) {
            assertCodigo(() -> comNome(nome), "USUARIO_NOME_OBRIGATORIO");
        }

        @Test
        void deveAparar() {
            assertThat(comNome("  Maria da Silva  ").getNome()).isEqualTo("Maria da Silva");
        }

        @Test
        void devePreservarConteudoInterno() {
            assertThat(comNome("José  D'Ávila-Souza").getNome()).isEqualTo("José  D'Ávila-Souza");
        }
    }

    @Nested
    class CpfValido {

        @ParameterizedTest
        @CsvSource({
                "52998224725, 52998224725",
                "529.982.247-25, 52998224725",
                "11144477735, 11144477735",
                "111.444.777-35, 11144477735",
                "12345678909, 12345678909",
                "93541134780, 93541134780",
                "00000000191, 00000000191",
                "10000000108, 10000000108",
                "100.000.006-04, 10000000604",
                "10000000280, 10000000280",
                "100.000.010-90, 10000001090"
        })
        void deveAceitarENormalizarParaOnzeDigitos(String entrada, String esperado) {
            assertThat(comCpf(entrada).getCpf()).isEqualTo(esperado);
        }

        @ParameterizedTest
        @ValueSource(strings = {"  52998224725  ", "\t529.982.247-25\n"})
        void deveAceitarEspacosNasPontas(String entrada) {
            assertThat(comCpf(entrada).getCpf()).isEqualTo(CPF);
        }
    }

    @Nested
    class CpfInvalido {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void deveRejeitarCpfAusente(String cpf) {
            assertCodigo(() -> comCpf(cpf), "USUARIO_CPF_INVALIDO");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "5299822472",          // 10 dígitos
                "529982247250",        // 12 dígitos
                "5299822472a",         // letra
                "abc52998224725xyz",   // texto ao redor
                "529.982.24725",       // máscara incompleta
                "529-982-247.25",      // separadores trocados
                "529.982.247/25",      // separador inválido
                "529 982 247 25",      // espaços internos
                "529.982.247-2",       // máscara com dígitos faltando
                "٥٢٩٩٨٢٢٤٧٢٥"          // dígitos não ASCII
        })
        void deveRejeitarFormatoInvalido(String cpf) {
            assertCodigo(() -> comCpf(cpf), "USUARIO_CPF_INVALIDO");
        }

        @ParameterizedTest
        @ValueSource(strings = {"52998224735", "529.982.247-35", "11144477745"})
        void deveRejeitarPrimeiroDigitoVerificadorErrado(String cpf) {
            assertCodigo(() -> comCpf(cpf), "USUARIO_CPF_INVALIDO");
        }

        @ParameterizedTest
        @ValueSource(strings = {"52998224724", "529.982.247-26", "11144477736", "10000000281"})
        void deveRejeitarSegundoDigitoVerificadorErrado(String cpf) {
            assertCodigo(() -> comCpf(cpf), "USUARIO_CPF_INVALIDO");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "00000000000", "11111111111", "22222222222", "33333333333", "44444444444",
                "55555555555", "66666666666", "77777777777", "88888888888", "999.999.999-99"
        })
        void deveRejeitarSequenciasRepetidas(String cpf) {
            assertCodigo(() -> comCpf(cpf), "USUARIO_CPF_INVALIDO");
        }
    }

    @Nested
    class EmailValido {

        @ParameterizedTest
        @CsvSource({
                "maria@exemplo.com.br, maria@exemplo.com.br",
                "Maria.Silva@Exemplo.COM, maria.silva@exemplo.com",
                "maria+sinal@exemplo.com, maria+sinal@exemplo.com",
                "maria_silva@exemplo.com, maria_silva@exemplo.com",
                "maria%silva@exemplo.com, maria%silva@exemplo.com",
                "maria-silva@exemplo.com, maria-silva@exemplo.com",
                "m@contato.sub.exemplo.org, m@contato.sub.exemplo.org",
                "maria@meu-dominio.com, maria@meu-dominio.com",
                "123@456.io, 123@456.io"
        })
        void deveAceitarENormalizar(String entrada, String esperado) {
            assertThat(comEmail(entrada).getEmail()).isEqualTo(esperado);
        }

        @Test
        void deveAparar() {
            assertThat(comEmail("  maria@exemplo.com.br\t").getEmail()).isEqualTo(EMAIL);
        }

        @Test
        void deveUsarLocaleNeutroNaConversaoParaMinusculas() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));

                assertThat(comEmail("MARIA@EXEMPLO.IO").getEmail()).isEqualTo("maria@exemplo.io");
            } finally {
                Locale.setDefault(original);
            }
        }

        @Test
        void deveAceitarLimitesDeTamanho() {
            String parteLocal = "a".repeat(64);
            String dominio = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".com";
            String email = parteLocal + "@" + dominio;

            assertThat(email).hasSize(254);
            assertThat(comEmail(email).getEmail()).isEqualTo(email);
        }
    }

    @Nested
    class EmailInvalido {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void deveRejeitarEmailAusente(String email) {
            assertCodigo(() -> comEmail(email), "USUARIO_EMAIL_INVALIDO");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "mariaexemplo.com",       // sem @
                "maria@@exemplo.com",     // dois @
                "maria@ex@emplo.com",     // dois @ separados
                "@exemplo.com",           // sem parte local
                "maria@",                 // sem domínio
                "maria@exemplo",          // sem TLD
                "maria@exemplo.c",        // TLD com 1 letra
                "maria@exemplo.c0m",      // TLD com dígito
                "maria@exemplo..com",     // rótulo vazio
                "maria@.exemplo.com",     // domínio começando com ponto
                "maria@exemplo.com.",     // domínio terminando com ponto
                ".maria@exemplo.com",     // parte local começando com ponto
                "maria.@exemplo.com",     // parte local terminando com ponto
                "ma..ria@exemplo.com",    // pontos consecutivos
                "ma ria@exemplo.com",     // espaço interno
                "maria@-exemplo.com",     // rótulo começando com hífen
                "maria@exemplo-.com",     // rótulo terminando com hífen
                "maria@exem_plo.com",     // sublinhado no domínio
                "maria(1)@exemplo.com",   // caractere não permitido
                "joão@exemplo.com",       // acento na parte local
                "maria@exemplação.com"    // acento no domínio
        })
        void deveRejeitarFormatoInvalido(String email) {
            assertCodigo(() -> comEmail(email), "USUARIO_EMAIL_INVALIDO");
        }

        @Test
        void deveRejeitarParteLocalComMaisDe64Caracteres() {
            assertCodigo(() -> comEmail("a".repeat(65) + "@exemplo.com"), "USUARIO_EMAIL_INVALIDO");
        }

        @Test
        void deveRejeitarEmailComMaisDe254Caracteres() {
            String dominio = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".com";
            String email = "a".repeat(64) + "@" + dominio;

            assertThat(email).hasSize(255);
            assertCodigo(() -> comEmail(email), "USUARIO_EMAIL_INVALIDO");
        }
    }

    @Nested
    class SenhaHash {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t\n"})
        void deveRejeitarSenhaHashAusenteOuEmBranco(String senhaHash) {
            assertCodigo(() -> comSenhaHash(senhaHash), "USUARIO_SENHA_OBRIGATORIA");
        }

        @ParameterizedTest
        @ValueSource(strings = {SENHA_HASH, " hash-com-espacos-nas-pontas ", "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"})
        void devePreservarOHashExatamente(String senhaHash) {
            assertThat(comSenhaHash(senhaHash).getSenhaHash()).isEqualTo(senhaHash);
        }
    }

    @Nested
    class Imutabilidade {

        private List<String> metodosPublicos() {
            return Arrays.stream(Usuario.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName)
                    .toList();
        }

        @Test
        void naoDeveExporMetodosDeAlteracao() {
            assertThat(metodosPublicos())
                    .noneMatch(nome -> nome.startsWith("set"))
                    .doesNotContain("ativar", "desativar");
            assertThat(metodosPublicos()).filteredOn(nome -> !nome.startsWith("get"))
                    .containsExactlyInAnyOrder("cadastrar", "reconstituir");
        }

        @Test
        void naoDeveExporConstrutorPublicoNemSemArgumentos() {
            assertThat(Usuario.class.getDeclaredConstructors())
                    .allMatch(c -> Modifier.isPrivate(c.getModifiers()))
                    .extracting(Constructor::getParameterCount)
                    .doesNotContain(0);
        }

        @Test
        void todosOsCamposDeInstanciaDevemSerFinais() {
            assertThat(Usuario.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .extracting(Field::getName)
                    .containsExactlyInAnyOrder("id", "nome", "cpf", "email", "senhaHash", "dataCadastro", "ativo");
            assertThat(Usuario.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> Modifier.isFinal(f.getModifiers()));
        }
    }
}
