package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.USUARIO_ID;
import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.comContatos;
import static br.edu.udf.cs.sinal.contato.application.ContatosDeTeste.transacaoDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListarContatosServiceTest {

    private ContatosRepository repository;
    private ListarContatosService service;

    @BeforeEach
    void configurar() {
        repository = mock(ContatosRepository.class);
        service = new ListarContatosService(repository);
    }

    @Test
    void deveListarNaOrdemDoAgregadoSemBloquearNemSalvar() {
        when(repository.buscarPorUsuario(USUARIO_ID)).thenReturn(comContatos(3));

        assertThat(service.executar(USUARIO_ID)).extracting(ContatoDeConfiancaResumo::id).containsExactly(1L, 2L, 3L);
        verify(repository, never()).buscarPorUsuarioParaAlteracao(any());
        verify(repository, never()).salvar(any());
    }

    @Test
    void usuarioSemContatosDeveReceberListaVazia() {
        when(repository.buscarPorUsuario(USUARIO_ID)).thenReturn(ContatosDeConfianca.novo(USUARIO_ID));

        assertThat(service.executar(USUARIO_ID)).isEmpty();
    }

    @Test
    void deveExecutarEmTransacaoSomenteLeitura() {
        assertThat(transacaoDe(ListarContatosService.class)).isNotNull()
                .extracting(t -> t.readOnly()).isEqualTo(true);
    }
}
