package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
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

class RemoverContatoServiceTest {

    private ContatosRepository repository;
    private RemoverContatoService service;

    @BeforeEach
    void configurar() {
        repository = mock(ContatosRepository.class);
        service = new RemoverContatoService(repository);
        when(repository.salvar(any())).thenAnswer(i -> simularPersistencia(i.getArgument(0)));
        when(repository.buscarPorUsuarioParaAlteracao(USUARIO_ID)).thenReturn(comContatos(3));
    }

    @Test
    void deveBloquearAntesDeSalvarSemOContatoRemovido() {
        service.executar(USUARIO_ID, 2L);

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).buscarPorUsuarioParaAlteracao(USUARIO_ID);
        ArgumentCaptor<ContatosDeConfianca> salvo = ArgumentCaptor.forClass(ContatosDeConfianca.class);
        ordem.verify(repository).salvar(salvo.capture());
        verify(repository, never()).buscarPorUsuario(any());
        assertThat(salvo.getValue().getContatos()).extracting(ContatoDeConfianca::getId).containsExactly(1L, 3L);
    }

    @Test
    void contatoInexistenteOuDeOutroUsuarioDeveSerRejeitadoSemSalvar() {
        assertThatThrownBy(() -> service.executar(USUARIO_ID, 99L))
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("CONTATO_NAO_ENCONTRADO");
        verify(repository, never()).salvar(any());
    }

    @Test
    void deveExecutarEmTransacaoDeEscrita() {
        assertThat(transacaoDe(RemoverContatoService.class)).isNotNull()
                .extracting(t -> t.readOnly()).isEqualTo(false);
    }
}
