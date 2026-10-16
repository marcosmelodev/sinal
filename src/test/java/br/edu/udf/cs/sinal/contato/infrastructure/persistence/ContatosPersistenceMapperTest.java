package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ContatosPersistenceMapperTest {

    private static final Long USUARIO_ID = 1L;

    private static ContatoConfiancaJpaEntity linha(long id, String nome, String telefone) {
        return new ContatoConfiancaJpaEntity(id, USUARIO_ID, nome, telefone);
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Test
    void deveReconstituirPreservandoOrdemIdsEDados() {
        ContatosDeConfianca agregado = ContatosPersistenceMapper.toDomain(USUARIO_ID, List.of(
                linha(3, "Ana", "61999998888"),
                linha(7, "Bruno", "6133334444")));

        assertThat(agregado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(agregado.getContatos())
                .extracting(ContatoDeConfianca::getId, ContatoDeConfianca::getNome, ContatoDeConfianca::getTelefone)
                .containsExactly(tuple(3L, "Ana", "61999998888"), tuple(7L, "Bruno", "6133334444"));
    }

    @Test
    void listaVaziaDeveGerarAgregadoVazio() {
        ContatosDeConfianca agregado = ContatosPersistenceMapper.toDomain(USUARIO_ID, List.of());

        assertThat(agregado.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(agregado.getContatos()).isEmpty();
    }

    @Test
    void leituraDeTelefoneInvalidoDeveViolarCT3() {
        assertCodigo(() -> ContatosPersistenceMapper.toDomain(USUARIO_ID, List.of(linha(1, "Ana", "123"))),
                "CONTATO_TELEFONE_INVALIDO");
    }

    @Test
    void leituraDeMaisDeCincoLinhasDeveViolarCT1() {
        List<ContatoConfiancaJpaEntity> seis = LongStream.rangeClosed(1, 6)
                .mapToObj(id -> linha(id, "Contato " + id, "61999998888"))
                .toList();

        assertCodigo(() -> ContatosPersistenceMapper.toDomain(USUARIO_ID, seis), "LIMITE_CONTATOS_EXCEDIDO");
    }

    @Test
    void contatoNovoDeveGerarEntidadeSemIdDoUsuario() {
        ContatoDeConfianca novo = ContatosDeConfianca.novo(USUARIO_ID).adicionar("Ana", "(61) 99999-8888");

        ContatoConfiancaJpaEntity entidade = ContatosPersistenceMapper.toNovaEntidade(USUARIO_ID, novo);

        assertThat(entidade.getId()).isNull();
        assertThat(entidade.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(entidade.getNome()).isEqualTo("Ana");
        assertThat(entidade.getTelefone()).isEqualTo("61999998888");
    }
}
