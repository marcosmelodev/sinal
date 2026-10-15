package br.edu.udf.cs.sinal.contato.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContatoDeConfiancaTest {

    private static final String NOME = "Ana Souza";
    private static final String TELEFONE = "61999998888";

    private static void assertCodigo(Runnable criacao, String codigo) {
        assertThatThrownBy(criacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Nested
    class Criacao {

        @Test
        void contatoNovoNaoTemId() {
            ContatoDeConfianca contato = ContatoDeConfianca.novo(NOME, TELEFONE);

            assertThat(contato.getId()).isNull();
            assertThat(contato.getNome()).isEqualTo(NOME);
            assertThat(contato.getTelefone()).isEqualTo(TELEFONE);
        }

        @Test
        void comDadosDevePreservarOIdEGerarNovaInstancia() {
            ContatoDeConfianca original = ContatoDeConfianca.reconstituir(7L, NOME, TELEFONE);

            ContatoDeConfianca editado = original.comDados("Ana Lima", "(61) 3333-4444");

            assertThat(editado).isNotSameAs(original);
            assertThat(editado.getId()).isEqualTo(7L);
            assertThat(editado.getNome()).isEqualTo("Ana Lima");
            assertThat(editado.getTelefone()).isEqualTo("6133334444");
            assertThat(original.getNome()).isEqualTo(NOME);
            assertThat(original.getTelefone()).isEqualTo(TELEFONE);
        }
    }

    @Nested
    class Reconstituicao {

        @Test
        void devePreservarIdENormalizarDados() {
            ContatoDeConfianca contato = ContatoDeConfianca.reconstituir(3L, "  Ana Souza ", "(61) 99999-8888");

            assertThat(contato.getId()).isEqualTo(3L);
            assertThat(contato.getNome()).isEqualTo(NOME);
            assertThat(contato.getTelefone()).isEqualTo(TELEFONE);
        }

        @Test
        void deveRejeitarIdNulo() {
            assertThatThrownBy(() -> ContatoDeConfianca.reconstituir(null, NOME, TELEFONE))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveAplicarCT2ECT3() {
            assertCodigo(() -> ContatoDeConfianca.reconstituir(1L, " ", TELEFONE), "CONTATO_NOME_OBRIGATORIO");
            assertCodigo(() -> ContatoDeConfianca.reconstituir(1L, NOME, null), "CONTATO_TELEFONE_OBRIGATORIO");
            assertCodigo(() -> ContatoDeConfianca.reconstituir(1L, NOME, "123"), "CONTATO_TELEFONE_INVALIDO");
        }
    }

    @Nested
    class CT2Nome {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   ", "\t\n"})
        void deveRejeitarNomeAusenteOuEmBranco(String nome) {
            assertCodigo(() -> ContatoDeConfianca.novo(nome, TELEFONE), "CONTATO_NOME_OBRIGATORIO");
        }

        @Test
        void deveAparar() {
            assertThat(ContatoDeConfianca.novo("  Ana Souza  ", TELEFONE).getNome()).isEqualTo(NOME);
        }

        @Test
        void devePreservarConteudoInterno() {
            assertThat(ContatoDeConfianca.novo("Mãe  (Joana)", TELEFONE).getNome()).isEqualTo("Mãe  (Joana)");
        }
    }

    @Nested
    class CT2TelefoneObrigatorio {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void deveRejeitarTelefoneAusenteOuEmBranco(String telefone) {
            assertCodigo(() -> ContatoDeConfianca.novo(NOME, telefone), "CONTATO_TELEFONE_OBRIGATORIO");
        }
    }

    @Nested
    class CT3TelefoneValido {

        @ParameterizedTest
        @CsvSource(delimiter = ';', value = {
                "61999998888; 61999998888",
                "(61) 99999-8888; 61999998888",
                "61 99999 8888; 61999998888",
                "(61)99999.8888; 61999998888",
                "+55 61 99999-8888; 61999998888",
                "+55 (61) 99999-8888; 61999998888",
                "5561999998888; 61999998888",
                "55 61 99999-8888; 61999998888",
                "6133334444; 6133334444",
                "(61) 3333-4444; 6133334444",
                "61 2333.4444; 6123334444",
                "(11) 4333-4444; 1143334444",
                "(11) 5333-4444; 1153334444",
                "+55 61 3333-4444; 6133334444",
                "556133334444; 6133334444",
                "55999998888; 55999998888",
                "5533334444; 5533334444",
                "(99) 99999-9999; 99999999999",
                "  (61) 99999-8888  ; 61999998888"
        })
        void deveAceitarENormalizarParaSomenteDigitos(String entrada, String esperado) {
            assertThat(ContatoDeConfianca.novo(NOME, entrada).getTelefone()).isEqualTo(esperado);
        }
    }

    @Nested
    class CT3TelefoneInvalido {

        @ParameterizedTest
        @ValueSource(strings = {
                "619999988",            // 9 dígitos
                "61999998888888",       // 14 dígitos
                "123456789012",         // 12 dígitos sem o código do país
                "1234567890123",        // 13 dígitos sem o código do país
                "(61) 9999A-8888",      // letra
                "61 99999+8888",        // "+" fora do início
                "++55 61 99999-8888",   // "+" repetido
                "+61 99999-8888",       // "+" sem o código do país 55
                "#61999998888",         // símbolo não permitido
                "61/99999-8888",        // separador não permitido
                "+",                    // somente o sinal
                "(-)",                  // somente formatação
                "00999998888",          // DDD 00
                "01999998888",          // DDD começando em 0
                "10999998888",          // DDD terminando em 0
                "6189999888",           // 10 dígitos: fixo não pode começar em 8
                "61899998888",          // 11 dígitos: celular deve começar em 9
                "6113334444",           // fixo começando em 1
                "6163334444",           // fixo começando em 6
                "6103334444",           // fixo começando em 0
                "+55 00 99999-8888",    // DDD 00 após o código do país
                "٦١٩٩٩٩٩٨٨٨٨"           // dígitos não ASCII
        })
        void deveRejeitar(String telefone) {
            assertCodigo(() -> ContatoDeConfianca.novo(NOME, telefone), "CONTATO_TELEFONE_INVALIDO");
        }
    }

    @Nested
    class Imutabilidade {

        @Test
        void naoDeveExporMetodosPublicosDeAlteracao() {
            assertThat(Arrays.stream(ContatoDeConfianca.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName))
                    .containsExactlyInAnyOrder("reconstituir", "getId", "getNome", "getTelefone");
        }

        @Test
        void naoDeveExporConstrutorPublico() {
            assertThat(ContatoDeConfianca.class.getDeclaredConstructors())
                    .allMatch(c -> Modifier.isPrivate(c.getModifiers()));
        }

        @Test
        void todosOsCamposDeInstanciaDevemSerFinais() {
            assertThat(ContatoDeConfianca.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .extracting(Field::getName)
                    .containsExactlyInAnyOrder("id", "nome", "telefone");
            assertThat(ContatoDeConfianca.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> Modifier.isFinal(f.getModifiers()));
        }

        @Test
        void classeDeveSerFinal() {
            assertThat(Modifier.isFinal(ContatoDeConfianca.class.getModifiers())).isTrue();
        }
    }
}
