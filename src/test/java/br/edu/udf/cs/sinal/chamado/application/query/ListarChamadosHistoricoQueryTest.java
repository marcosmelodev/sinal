package br.edu.udf.cs.sinal.chamado.application.query;

import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListarChamadosHistoricoQueryTest {

    private static final Long USUARIO_ID = 1L;
    private static final Instant T0 = Instant.parse("2026-10-17T12:00:00.123456Z");

    private ChamadoConsulta consulta;
    private ListarChamadosHistoricoQuery query;

    @BeforeEach
    void configurar() {
        consulta = mock(ChamadoConsulta.class);
        query = new ListarChamadosHistoricoQuery(consulta);
    }

    private static ChamadoHistoricoItem item(long id) {
        return new ChamadoHistoricoItem(id, ModalidadeChamado.PADRAO, OrgaoSeguranca.SAMU, 12L, "Convulsão",
                EstadoChamado.CRIADO, false, T0.minusSeconds(id));
    }

    private static List<ChamadoHistoricoItem> itens(int quantidade) {
        return LongStream.rangeClosed(1, quantidade).mapToObj(ListarChamadosHistoricoQueryTest::item).toList();
    }

    private static String base64(String conteudo) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(conteudo.getBytes(StandardCharsets.US_ASCII));
    }

    private static void assertValorInvalido(Runnable operacao, String campo) {
        assertThatThrownBy(operacao::run).isInstanceOfSatisfying(AplicacaoException.class, e -> {
            assertThat(e.getCodigo()).isEqualTo("VALOR_INVALIDO");
            assertThat(e.getCampos()).containsExactly(campo);
        });
    }

    @Test
    void deveBuscarUmItemAMaisEDevolverCursorQuandoHaProximaPagina() {
        when(consulta.listarHistorico(eq(USUARIO_ID), isNull(), eq(3))).thenReturn(itens(3));

        PaginaDeHistorico pagina = query.executar(USUARIO_ID, 2, null);

        assertThat(pagina.itens()).extracting(ChamadoHistoricoItem::id).containsExactly(1L, 2L);
        assertThat(pagina.proximoCursor()).isNotNull();
        assertThat(ListarChamadosHistoricoQuery.decodificar(pagina.proximoCursor()))
                .isEqualTo(new PosicaoNoHistorico(item(2).dataCriacao(), 2L));
    }

    @Test
    void ultimaPaginaNaoDeveTerCursor() {
        when(consulta.listarHistorico(eq(USUARIO_ID), isNull(), eq(3))).thenReturn(itens(2));

        PaginaDeHistorico pagina = query.executar(USUARIO_ID, 2, null);

        assertThat(pagina.itens()).hasSize(2);
        assertThat(pagina.proximoCursor()).isNull();
    }

    @Test
    void semLimiteDeveUsarOPadraoDe20() {
        when(consulta.listarHistorico(eq(USUARIO_ID), isNull(), eq(21))).thenReturn(List.of());

        assertThat(query.executar(USUARIO_ID, null, null).itens()).isEmpty();
        verify(consulta).listarHistorico(USUARIO_ID, null, 21);
    }

    @Test
    void cursorDeveSerRepassadoComoPosicao() {
        PosicaoNoHistorico posicao = new PosicaoNoHistorico(T0, 42L);
        when(consulta.listarHistorico(any(), any(), anyInt())).thenReturn(List.of());

        query.executar(USUARIO_ID, 10, ListarChamadosHistoricoQuery.codificar(posicao));

        verify(consulta).listarHistorico(USUARIO_ID, posicao, 11);
    }

    @Test
    void cursorDevePreservarMicrossegundosNaIdaEVolta() {
        for (Instant data : List.of(T0, Instant.parse("1969-12-31T23:59:59.999999Z"), Instant.EPOCH)) {
            PosicaoNoHistorico posicao = new PosicaoNoHistorico(data, 7L);

            assertThat(ListarChamadosHistoricoQuery.decodificar(ListarChamadosHistoricoQuery.codificar(posicao)))
                    .isEqualTo(posicao);
        }
    }

    @Test
    void cursorDeveSerOpacoEUrlSeguro() {
        String cursor = ListarChamadosHistoricoQuery.codificar(new PosicaoNoHistorico(T0, 42L));

        assertThat(cursor).matches("[A-Za-z0-9_-]+").doesNotContain("42");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 51, 1000})
    void limiteForaDaFaixaDeveSerRejeitado(int limite) {
        assertValorInvalido(() -> query.executar(USUARIO_ID, limite, null), "limite");
        verifyNoInteractions(consulta);
    }

    @Test
    void limitesExatosDevemSerAceitos() {
        when(consulta.listarHistorico(any(), any(), anyInt())).thenReturn(List.of());

        query.executar(USUARIO_ID, 1, null);
        query.executar(USUARIO_ID, 50, null);

        verify(consulta).listarHistorico(USUARIO_ID, null, 2);
        verify(consulta).listarHistorico(USUARIO_ID, null, 51);
    }

    @Test
    void cursorMalFormadoDeveSerRejeitado() {
        List<String> invalidos = List.of("", "!!!", "não-base64", base64("abc"), base64("123"), base64("123."),
                base64(".5"), base64("123.0"), base64("123.-5"), base64("1.2.3"), base64("123.abc"),
                base64("99999999999999999999.1"));

        for (String cursor : invalidos) {
            assertValorInvalido(() -> query.executar(USUARIO_ID, 10, cursor), "cursor");
        }
        verifyNoInteractions(consulta);
    }
}
