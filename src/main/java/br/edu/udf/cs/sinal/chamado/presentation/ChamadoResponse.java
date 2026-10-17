package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.chamado.application.ChamadoRegistradoResultado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.time.Instant;

// codigoSimulacao "#SIM-{id}" é formatado na Presentation (Etapa 5 N3). modoEncaminhamento informa que o MVP é
// simulado sem propriedade artificial no Chamado (Etapa 5 §11). Os textos exibidos ao usuário são da UI.
public record ChamadoResponse(Long id, String codigoSimulacao, ModalidadeChamado modalidade, OrgaoSeguranca orgao,
                              Long ocorrenciaId, Localizacao localizacao, Boolean baixaPrecisao,
                              String detalheAdicional, EstadoChamado estado, Instant dataCriacao,
                              String modoEncaminhamento) {

    private static final String MODO_ENCAMINHAMENTO = "SIMULADO";

    public record Localizacao(Double latitude, Double longitude, Float precisao, Instant timestamp) {

        @Override
        public String toString() {
            return "Localizacao[***]";
        }
    }

    static ChamadoResponse de(ChamadoRegistradoResultado resultado) {
        return new ChamadoResponse(resultado.id(), "#SIM-" + resultado.id(), resultado.modalidade(), resultado.orgao(),
                resultado.ocorrenciaId(), new Localizacao(resultado.latitude(), resultado.longitude(),
                resultado.precisao(), resultado.timestampLocalizacao()), resultado.baixaPrecisao(),
                resultado.detalheAdicional(), resultado.estado(), resultado.dataCriacao(), MODO_ENCAMINHAMENTO);
    }

    @Override
    public String toString() {
        return "ChamadoResponse[id=" + id + ", modalidade=" + modalidade + ", orgao=" + orgao + ", estado=" + estado + "]";
    }
}
