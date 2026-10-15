package br.edu.udf.cs.sinal.contato.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContatosDeConfiancaTest {

    private static final Long USUARIO_ID = 1L;
    private static final String NOME = "Ana Souza";
    private static final String TELEFONE = "61999998888";

    private static ContatoDeConfianca contato(long id) {
        return ContatoDeConfianca.reconstituir(id, "Contato " + id, String.format("619999990%02d", id));
    }

    private static List<ContatoDeConfianca> contatos(int quantidade) {
        return LongStream.rangeClosed(1, quantidade).mapToObj(ContatosDeConfiancaTest::contato).toList();
    }

    private static ContatosDeConfianca comContatos(int quantidade) {
        return ContatosDeConfianca.reconstituir(USUARIO_ID, contatos(quantidade));
    }

    private static List<Long> ids(ContatosDeConfianca agregado) {
        return agregado.getContatos().stream().map(ContatoDeConfianca::getId).toList();
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    // Garante que uma operação rejeitada não altera a lista.
    private static void assertRejeitadaSemAlterar(ContatosDeConfianca agregado,
                                                  Consumer<ContatosDeConfianca> operacao, String codigo) {
        List<ContatoDeConfianca> antes = agregado.getContatos();

        assertCodigo(() -> operacao.accept(agregado), codigo);

        assertThat(agregado.getContatos()).containsExactlyElementsOf(antes);
    }

    @Nested
    class Criacao {

        @Test
        void novoDeveIniciarVazio() {
            ContatosDeConfianca agregado = ContatosDeConfianca.novo(USUARIO_ID);

            assertThat(agregado.getUsuarioId()).isEqualTo(USUARIO_ID);
            assertThat(agregado.getContatos()).isEmpty();
        }

        @Test
        void novoDeveRejeitarUsuarioNulo() {
            assertThatThrownBy(() -> ContatosDeConfianca.novo(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Reconstituicao {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5})
        void deveAceitarDeZeroACincoContatos(int quantidade) {
            assertThat(comContatos(quantidade).getContatos()).hasSize(quantidade);
        }

        @Test
        void devePreservarAOrdem() {
            List<ContatoDeConfianca> lista = List.of(contato(3), contato(1), contato(2));

            assertThat(ids(ContatosDeConfianca.reconstituir(USUARIO_ID, lista))).containsExactly(3L, 1L, 2L);
        }

        @Test
        void naoDeveCompartilharAListaRecebida() {
            List<ContatoDeConfianca> lista = new ArrayList<>(contatos(2));
            ContatosDeConfianca agregado = ContatosDeConfianca.reconstituir(USUARIO_ID, lista);

            lista.add(contato(3));

            assertThat(agregado.getContatos()).hasSize(2);
        }

        @Test
        void deveRejeitarMaisDeCincoContatosComCT1() {
            assertCodigo(() -> comContatos(6), "LIMITE_CONTATOS_EXCEDIDO");
        }

        @Test
        void deveRejeitarUsuarioNulo() {
            assertThatThrownBy(() -> ContatosDeConfianca.reconstituir(null, contatos(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarListaNula() {
            assertThatThrownBy(() -> ContatosDeConfianca.reconstituir(USUARIO_ID, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarElementoNulo() {
            List<ContatoDeConfianca> lista = new ArrayList<>(contatos(1));
            lista.add(null);

            assertThatThrownBy(() -> ContatosDeConfianca.reconstituir(USUARIO_ID, lista))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarContatoSemId() {
            List<ContatoDeConfianca> lista = List.of(contato(1), ContatoDeConfianca.novo(NOME, TELEFONE));

            assertThatThrownBy(() -> ContatosDeConfianca.reconstituir(USUARIO_ID, lista))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deveRejeitarIdsRepetidos() {
            List<ContatoDeConfianca> lista = List.of(contato(1), contato(1));

            assertThatThrownBy(() -> ContatosDeConfianca.reconstituir(USUARIO_ID, lista))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CT1Limite {

        @Test
        void deveAdicionarAteCincoContatos() {
            ContatosDeConfianca agregado = ContatosDeConfianca.novo(USUARIO_ID);

            for (int i = 1; i <= 5; i++) {
                agregado.adicionar("Contato " + i, TELEFONE);
                assertThat(agregado.getContatos()).hasSize(i);
            }
        }

        @Test
        void deveRejeitarOSextoContatoSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(5), a -> a.adicionar(NOME, TELEFONE), "LIMITE_CONTATOS_EXCEDIDO");
        }

        @Test
        void limiteDeveTerPrioridadeSobreDadosInvalidos() {
            assertRejeitadaSemAlterar(comContatos(5), a -> a.adicionar(null, null), "LIMITE_CONTATOS_EXCEDIDO");
        }

        @Test
        void removerDeveLiberarVaga() {
            ContatosDeConfianca agregado = comContatos(5);

            agregado.remover(3L);
            agregado.adicionar(NOME, TELEFONE);

            assertThat(agregado.getContatos()).hasSize(5);
        }

        @Test
        void limiteDeveSerCinco() {
            assertThat(ContatosDeConfianca.LIMITE_CONTATOS).isEqualTo(5);
        }
    }

    @Nested
    class Adicionar {

        @Test
        void deveDevolverOContatoCriadoSemIdENoFimDaLista() {
            ContatosDeConfianca agregado = comContatos(2);

            ContatoDeConfianca criado = agregado.adicionar("  Ana Souza ", "(61) 99999-8888");

            assertThat(criado.getId()).isNull();
            assertThat(criado.getNome()).isEqualTo(NOME);
            assertThat(criado.getTelefone()).isEqualTo(TELEFONE);
            assertThat(agregado.getContatos()).hasSize(3).last().isSameAs(criado);
        }

        @Test
        void deveRejeitarNomeInvalidoSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(2), a -> a.adicionar(" ", TELEFONE), "CONTATO_NOME_OBRIGATORIO");
        }

        @Test
        void deveRejeitarTelefoneAusenteSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(2), a -> a.adicionar(NOME, ""), "CONTATO_TELEFONE_OBRIGATORIO");
        }

        @Test
        void deveRejeitarTelefoneInvalidoSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(2), a -> a.adicionar(NOME, "12345"), "CONTATO_TELEFONE_INVALIDO");
        }

        @Test
        void deveAceitarTelefonesRepetidos() {
            ContatosDeConfianca agregado = ContatosDeConfianca.novo(USUARIO_ID);

            agregado.adicionar("Ana", "(61) 99999-8888");
            agregado.adicionar("Bruno", "+55 61 99999-8888");

            assertThat(agregado.getContatos()).extracting(ContatoDeConfianca::getTelefone)
                    .containsExactly(TELEFONE, TELEFONE);
        }
    }

    @Nested
    class CT4Editar {

        @Test
        void deveSubstituirNomeETelefonePreservandoIdEPosicao() {
            ContatosDeConfianca agregado = comContatos(3);

            agregado.editar(2L, "  Ana Lima ", "(61) 3333-4444");

            assertThat(ids(agregado)).containsExactly(1L, 2L, 3L);
            ContatoDeConfianca editado = agregado.getContatos().get(1);
            assertThat(editado.getNome()).isEqualTo("Ana Lima");
            assertThat(editado.getTelefone()).isEqualTo("6133334444");
        }

        @Test
        void naoDeveAlterarOsDemaisContatos() {
            ContatosDeConfianca agregado = comContatos(3);
            List<ContatoDeConfianca> antes = agregado.getContatos();

            agregado.editar(2L, NOME, TELEFONE);

            assertThat(agregado.getContatos().get(0)).isSameAs(antes.get(0));
            assertThat(agregado.getContatos().get(2)).isSameAs(antes.get(2));
        }

        @Test
        void deveRejeitarContatoInexistente() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(99L, NOME, TELEFONE), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarIdNulo() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(null, NOME, TELEFONE), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarContatoDeOutraLista() {
            ContatosDeConfianca outroUsuario = ContatosDeConfianca.reconstituir(2L, List.of(contato(10)));

            assertThat(outroUsuario.possuiContato(10L)).isTrue();
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(10L, NOME, TELEFONE), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void naoDeveLocalizarContatoNovoAindaSemId() {
            ContatosDeConfianca agregado = comContatos(1);
            agregado.adicionar(NOME, TELEFONE);

            assertRejeitadaSemAlterar(agregado, a -> a.editar(null, "Outro", TELEFONE), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarNomeInvalidoSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(2L, "", TELEFONE), "CONTATO_NOME_OBRIGATORIO");
        }

        @Test
        void deveRejeitarTelefoneAusenteSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(2L, NOME, null), "CONTATO_TELEFONE_OBRIGATORIO");
        }

        @Test
        void deveRejeitarTelefoneInvalidoSemAlterarALista() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(2L, NOME, "6113334444"),
                    "CONTATO_TELEFONE_INVALIDO");
        }

        @Test
        void naoEncontradoDeveTerPrioridadeSobreDadosInvalidos() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.editar(99L, null, null), "CONTATO_NAO_ENCONTRADO");
        }
    }

    @Nested
    class CT4Remover {

        @ParameterizedTest
        @ValueSource(longs = {1L, 3L, 5L})
        void deveRemoverPreservandoAOrdemDosDemais(long id) {
            ContatosDeConfianca agregado = comContatos(5);

            agregado.remover(id);

            assertThat(ids(agregado)).hasSize(4).doesNotContain(id).isSorted();
        }

        @Test
        void deveRemoverOUnicoContato() {
            ContatosDeConfianca agregado = comContatos(1);

            agregado.remover(1L);

            assertThat(agregado.getContatos()).isEmpty();
        }

        @Test
        void deveRejeitarContatoInexistente() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.remover(99L), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarIdNulo() {
            assertRejeitadaSemAlterar(comContatos(3), a -> a.remover(null), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarRemocaoRepetida() {
            ContatosDeConfianca agregado = comContatos(3);
            agregado.remover(2L);

            assertRejeitadaSemAlterar(agregado, a -> a.remover(2L), "CONTATO_NAO_ENCONTRADO");
        }

        @Test
        void deveRejeitarEmListaVazia() {
            assertRejeitadaSemAlterar(ContatosDeConfianca.novo(USUARIO_ID), a -> a.remover(1L),
                    "CONTATO_NAO_ENCONTRADO");
        }
    }

    @Nested
    class PossuiContato {

        @Test
        void deveIndicarContatoDaLista() {
            assertThat(comContatos(3).possuiContato(2L)).isTrue();
        }

        @Test
        void naoDeveIndicarContatoInexistente() {
            assertThat(comContatos(3).possuiContato(99L)).isFalse();
        }

        @Test
        void naoDeveIndicarIdNulo() {
            ContatosDeConfianca agregado = comContatos(1);
            agregado.adicionar(NOME, TELEFONE);

            assertThat(agregado.possuiContato(null)).isFalse();
        }

        @Test
        void naoDeveIndicarContatoRemovido() {
            ContatosDeConfianca agregado = comContatos(3);
            agregado.remover(2L);

            assertThat(agregado.possuiContato(2L)).isFalse();
        }
    }

    @Nested
    class ColecaoProtegida {

        @Test
        void listaDeContatosNaoDeveSerModificavel() {
            ContatosDeConfianca agregado = comContatos(2);
            List<ContatoDeConfianca> lista = agregado.getContatos();

            assertThatThrownBy(() -> lista.add(contato(9))).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> lista.remove(0)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(lista::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThat(agregado.getContatos()).hasSize(2);
        }

        @Test
        void listaObtidaAntesNaoDeveRefletirAlteracoes() {
            ContatosDeConfianca agregado = comContatos(2);
            List<ContatoDeConfianca> antes = agregado.getContatos();

            agregado.adicionar(NOME, TELEFONE);
            agregado.editar(1L, "Outro", TELEFONE);

            assertThat(antes).hasSize(2);
            assertThat(antes.get(0).getNome()).isEqualTo("Contato 1");
        }
    }

    @Nested
    class Reflexao {

        @Test
        void naoDeveExporMetodosPublicosAlemDasOperacoes() {
            assertThat(Arrays.stream(ContatosDeConfianca.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .map(Method::getName))
                    .containsExactlyInAnyOrder("novo", "reconstituir", "adicionar", "editar", "remover",
                            "possuiContato", "getUsuarioId", "getContatos");
        }

        @Test
        void naoDeveExporConstrutorPublico() {
            assertThat(ContatosDeConfianca.class.getDeclaredConstructors())
                    .allMatch(c -> Modifier.isPrivate(c.getModifiers()));
        }

        @Test
        void camposDeInstanciaDevemSerFinais() {
            assertThat(ContatosDeConfianca.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .extracting(Field::getName)
                    .containsExactlyInAnyOrder("usuarioId", "contatos");
            assertThat(ContatosDeConfianca.class.getDeclaredFields())
                    .filteredOn(f -> !Modifier.isStatic(f.getModifiers()))
                    .allMatch(f -> Modifier.isFinal(f.getModifiers()));
        }
    }
}
