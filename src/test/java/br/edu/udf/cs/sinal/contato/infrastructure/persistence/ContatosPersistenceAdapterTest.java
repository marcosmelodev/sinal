package br.edu.udf.cs.sinal.contato.infrastructure.persistence;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ContatosPersistenceAdapter.class})
class ContatosPersistenceAdapterTest {

    private static final String TELEFONE = "61999998888";

    @Autowired
    private ContatosPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private Long usuarioA;
    private Long usuarioB;

    @BeforeEach
    void criarUsuarios() {
        usuarioA = inserirUsuario("52998224725", "a@exemplo.com");
        usuarioB = inserirUsuario("11144477735", "b@exemplo.com");
    }

    private Long inserirUsuario(String cpf, String email) {
        entityManager.createNativeQuery("""
                        INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro)
                        VALUES ('Usuário', ?1, ?2, 'hash', TRUE, ?3)""")
                .setParameter(1, cpf).setParameter(2, email)
                .setParameter(3, Timestamp.from(Instant.parse("2026-10-15T12:00:00Z")))
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT id FROM usuario WHERE cpf = ?1")
                .setParameter(1, cpf).getSingleResult()).longValue();
    }

    private void inserirLinha(Long usuarioId, String nome, String telefone) {
        entityManager.createNativeQuery(
                        "INSERT INTO contato_confianca (usuario_id, nome, telefone) VALUES (?1, ?2, ?3)")
                .setParameter(1, usuarioId).setParameter(2, nome).setParameter(3, telefone)
                .executeUpdate();
    }

    // Força a ida ao banco: grava o pendente e descarta o cache de primeiro nível.
    private void sincronizarComOBanco() {
        entityManager.flush();
        entityManager.clear();
    }

    private ContatosDeConfianca salvarComNomes(Long usuarioId, String... nomes) {
        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioId);
        for (String nome : nomes) {
            agregado.adicionar(nome, TELEFONE);
        }
        ContatosDeConfianca salvo = adapter.salvar(agregado);
        sincronizarComOBanco();
        return salvo;
    }

    private static List<Long> ids(ContatosDeConfianca agregado) {
        return agregado.getContatos().stream().map(ContatoDeConfianca::getId).toList();
    }

    private static List<String> nomes(ContatosDeConfianca agregado) {
        return agregado.getContatos().stream().map(ContatoDeConfianca::getNome).toList();
    }

    @Test
    void usuarioSemContatosDeveDevolverAgregadoVazio() {
        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);

        assertThat(agregado).isNotNull();
        assertThat(agregado.getUsuarioId()).isEqualTo(usuarioA);
        assertThat(agregado.getContatos()).isEmpty();
    }

    @Test
    void salvarAgregadoVazioNaoDeveGravarLinhas() {
        ContatosDeConfianca salvo = adapter.salvar(ContatosDeConfianca.novo(usuarioA));
        sincronizarComOBanco();

        assertThat(salvo.getContatos()).isEmpty();
        assertThat(adapter.buscarPorUsuario(usuarioA).getContatos()).isEmpty();
    }

    @Test
    void deveAdicionarAteCincoContatosComIdsGeradosEmOrdem() {
        ContatosDeConfianca agregado = ContatosDeConfianca.novo(usuarioA);
        agregado.adicionar("Ana", "(61) 99999-8888");
        agregado.adicionar("Bruno", "+55 61 3333-4444");
        agregado.adicionar("Carla", TELEFONE);
        agregado.adicionar("Davi", TELEFONE);
        agregado.adicionar("Eva", TELEFONE);

        ContatosDeConfianca salvo = adapter.salvar(agregado);
        sincronizarComOBanco();

        assertThat(ids(salvo)).hasSize(5).doesNotContainNull().isSorted();
        ContatosDeConfianca lido = adapter.buscarPorUsuario(usuarioA);
        assertThat(lido.getContatos())
                .extracting(ContatoDeConfianca::getId, ContatoDeConfianca::getNome, ContatoDeConfianca::getTelefone)
                .containsExactly(
                        tuple(ids(salvo).get(0), "Ana", "61999998888"),
                        tuple(ids(salvo).get(1), "Bruno", "6133334444"),
                        tuple(ids(salvo).get(2), "Carla", TELEFONE),
                        tuple(ids(salvo).get(3), "Davi", TELEFONE),
                        tuple(ids(salvo).get(4), "Eva", TELEFONE));
    }

    @Test
    void deveAceitarTelefonesRepetidos() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno");

        assertThat(adapter.buscarPorUsuario(usuarioA).getContatos())
                .extracting(ContatoDeConfianca::getTelefone).containsExactly(TELEFONE, TELEFONE);
        assertThat(ids(salvo)).doesNotHaveDuplicates();
    }

    @Test
    void editarDevePreservarIdEPosicaoEAlterarSomenteOContato() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno", "Carla");
        Long idDoMeio = ids(salvo).get(1);

        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);
        agregado.editar(idDoMeio, "Bruno Lima", "(61) 3333-4444");
        adapter.salvar(agregado);
        sincronizarComOBanco();

        ContatosDeConfianca lido = adapter.buscarPorUsuario(usuarioA);
        assertThat(ids(lido)).isEqualTo(ids(salvo));
        assertThat(lido.getContatos())
                .extracting(ContatoDeConfianca::getNome, ContatoDeConfianca::getTelefone)
                .containsExactly(tuple("Ana", TELEFONE), tuple("Bruno Lima", "6133334444"), tuple("Carla", TELEFONE));
    }

    @Test
    void removerDeveApagarALinhaEPreservarOsDemaisIds() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno", "Carla");
        Long removido = ids(salvo).get(1);

        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);
        agregado.remover(removido);
        adapter.salvar(agregado);
        sincronizarComOBanco();

        assertThat(ids(adapter.buscarPorUsuario(usuarioA))).containsExactly(ids(salvo).get(0), ids(salvo).get(2));
        assertThat(entityManager.find(ContatoConfiancaJpaEntity.class, removido)).isNull();
    }

    @Test
    void deveSincronizarRemocaoEdicaoEAdicaoNoMesmoSalvar() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno", "Carla");
        List<Long> originais = ids(salvo);

        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);
        agregado.remover(originais.get(0));
        agregado.editar(originais.get(2), "Carla Souza", TELEFONE);
        agregado.adicionar("Davi", TELEFONE);
        ContatosDeConfianca resultado = adapter.salvar(agregado);
        sincronizarComOBanco();

        ContatosDeConfianca lido = adapter.buscarPorUsuario(usuarioA);
        assertThat(nomes(lido)).containsExactly("Bruno", "Carla Souza", "Davi");
        assertThat(ids(lido).subList(0, 2)).containsExactly(originais.get(1), originais.get(2));
        assertThat(ids(lido).get(2)).isGreaterThan(originais.get(2));
        assertThat(ids(resultado)).isEqualTo(ids(lido));
    }

    @Test
    void contatoNovoDeveFicarPorUltimoMesmoOcupandoVagaDeRemovido() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno", "Carla", "Davi", "Eva");

        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);
        agregado.remover(ids(salvo).get(0));
        agregado.adicionar("Fábio", TELEFONE);
        adapter.salvar(agregado);
        sincronizarComOBanco();

        ContatosDeConfianca lido = adapter.buscarPorUsuario(usuarioA);
        assertThat(nomes(lido)).containsExactly("Bruno", "Carla", "Davi", "Eva", "Fábio");
        assertThat(ids(lido)).isSorted();
    }

    @Test
    void contatosDeOutroUsuarioNaoDevemSerLidosNemApagados() {
        salvarComNomes(usuarioB, "Contato de B");
        salvarComNomes(usuarioA, "Contato de A");

        ContatosDeConfianca agregadoA = adapter.buscarPorUsuario(usuarioA);
        agregadoA.remover(ids(agregadoA).get(0));
        adapter.salvar(agregadoA);
        sincronizarComOBanco();

        assertThat(adapter.buscarPorUsuario(usuarioA).getContatos()).isEmpty();
        assertThat(nomes(adapter.buscarPorUsuario(usuarioB))).containsExactly("Contato de B");
    }

    @Test
    void deveRejeitarContatosDeUsuarioInexistente() {
        ContatosDeConfianca agregado = ContatosDeConfianca.novo(999_999L);
        agregado.adicionar("Ana", TELEFONE);

        assertThatThrownBy(() -> adapter.salvar(agregado)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveRejeitarContatoQueNaoExisteMaisNoBanco() {
        ContatosDeConfianca salvo = salvarComNomes(usuarioA, "Ana", "Bruno");
        ContatosDeConfianca agregado = adapter.buscarPorUsuario(usuarioA);

        entityManager.createNativeQuery("DELETE FROM contato_confianca WHERE id = ?1")
                .setParameter(1, ids(salvo).get(0)).executeUpdate();
        sincronizarComOBanco();

        assertThatThrownBy(() -> adapter.salvar(agregado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void leituraDeMaisDeCincoLinhasDeveViolarCT1() {
        for (int i = 1; i <= 6; i++) {
            inserirLinha(usuarioA, "Contato " + i, TELEFONE);
        }

        assertThatThrownBy(() -> adapter.buscarPorUsuario(usuarioA))
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("LIMITE_CONTATOS_EXCEDIDO");
    }

    @Test
    void buscarParaAlteracaoDeveDevolverOsContatosDentroDeTransacao() {
        salvarComNomes(usuarioA, "Ana", "Bruno");

        assertThat(nomes(adapter.buscarPorUsuarioParaAlteracao(usuarioA))).containsExactly("Ana", "Bruno");
    }
}
