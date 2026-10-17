package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.USUARIO_ID;
import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.comContatos;
import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.simularPersistencia;
import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.transacaoDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EditarContatoServiceTest {

    private ContatosRepository repository;
    private EditarContatoService service;

    @BeforeEach
    void configurar() {
        repository = mock(ContatosRepository.class);
        service = new EditarContatoService(repository);
        when(repository.salvar(any())).thenAnswer(i -> simularPersistencia(i.getArgument(0)));
        when(repository.buscarPorUsuarioParaAlteracao(USUARIO_ID)).thenReturn(comContatos(3));
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Test
    void deveBloquearAntesDeSalvarEPreservarOId() {
        ContatoDeConfiancaResumo editado = service.executar(USUARIO_ID, 2L, "Bruno Lima", "+55 61 3333-4444");

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).buscarPorUsuarioParaAlteracao(USUARIO_ID);
        ordem.verify(repository).salvar(any(ContatosDeConfianca.class));
        verify(repository, never()).buscarPorUsuario(any());
        assertThat(editado.id()).isEqualTo(2L);
        assertThat(editado.nome()).isEqualTo("Bruno Lima");
        assertThat(editado.telefone()).isEqualTo("6133334444");
    }

    @Test
    void deveSalvarOAgregadoComOContatoAlterado() {
        service.executar(USUARIO_ID, 2L, "Bruno Lima", "61999998888");

        ArgumentCaptor<ContatosDeConfianca> salvo = ArgumentCaptor.forClass(ContatosDeConfianca.class);
        verify(repository).salvar(salvo.capture());
        assertThat(salvo.getValue().getContatos()).hasSize(3);
        assertThat(salvo.getValue().getContatos().get(1).getNome()).isEqualTo("Bruno Lima");
    }

    @Test
    void contatoInexistenteOuDeOutroUsuarioDeveSerRejeitadoSemSalvar() {
        assertCodigo(() -> service.executar(USUARIO_ID, 99L, "Bruno", "61999998888"), "CONTATO_NAO_ENCONTRADO");
        verify(repository, never()).salvar(any());
    }

    @Test
    void dadosInvalidosDevemSerRejeitadosSemSalvar() {
        assertCodigo(() -> service.executar(USUARIO_ID, 2L, " ", "61999998888"), "CONTATO_NOME_OBRIGATORIO");
        assertCodigo(() -> service.executar(USUARIO_ID, 2L, "Bruno", "6113334444"), "CONTATO_TELEFONE_INVALIDO");
        verify(repository, never()).salvar(any());
    }

    @Test
    void deveExecutarEmTransacaoDeEscrita() {
        assertThat(transacaoDe(EditarContatoService.class)).isNotNull()
                .extracting(t -> t.readOnly()).isEqualTo(false);
    }
}
