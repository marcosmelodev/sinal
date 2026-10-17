package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class AdicionarContatoServiceTest {

    private ContatosRepository repository;
    private AdicionarContatoService service;

    @BeforeEach
    void configurar() {
        repository = mock(ContatosRepository.class);
        service = new AdicionarContatoService(repository);
        when(repository.salvar(any())).thenAnswer(i -> simularPersistencia(i.getArgument(0)));
    }

    private static void assertCodigo(Runnable operacao, String codigo) {
        assertThatThrownBy(operacao::run)
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo(codigo);
    }

    @Test
    void deveBloquearAntesDeSalvarEDevolverOContatoCriado() {
        when(repository.buscarPorUsuarioParaAlteracao(USUARIO_ID)).thenReturn(comContatos(2));

        ContatoDeConfiancaResumo criado = service.executar(USUARIO_ID, "  Ana  ", "(61) 3333-4444");

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).buscarPorUsuarioParaAlteracao(USUARIO_ID);
        ordem.verify(repository).salvar(any(ContatosDeConfianca.class));
        verify(repository, never()).buscarPorUsuario(any());
        assertThat(criado.id()).isEqualTo(102L);
        assertThat(criado.nome()).isEqualTo("Ana");
        assertThat(criado.telefone()).isEqualTo("6133334444");
    }

    @Test
    void sextoContatoDeveSerRejeitadoSemSalvar() {
        when(repository.buscarPorUsuarioParaAlteracao(USUARIO_ID)).thenReturn(comContatos(5));

        assertCodigo(() -> service.executar(USUARIO_ID, "Ana", "61999998888"), "LIMITE_CONTATOS_EXCEDIDO");
        verify(repository, never()).salvar(any());
    }

    @Test
    void telefoneInvalidoDeveSerRejeitadoSemSalvar() {
        when(repository.buscarPorUsuarioParaAlteracao(USUARIO_ID)).thenReturn(comContatos(1));

        assertCodigo(() -> service.executar(USUARIO_ID, "Ana", "123"), "CONTATO_TELEFONE_INVALIDO");
        verify(repository, never()).salvar(any());
    }

    @Test
    void deveExecutarEmTransacaoDeEscrita() {
        assertThat(transacaoDe(AdicionarContatoService.class)).isNotNull()
                .extracting(t -> t.readOnly()).isEqualTo(false);
    }

    @Test
    void toStringDoResumoNaoDeveExporOTelefone() {
        assertThat(new ContatoDeConfiancaResumo(1L, "Ana", "61999998888").toString()).doesNotContain("61999998888");
    }
}
