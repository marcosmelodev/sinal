package br.edu.udf.cs.sinal.chamado.application;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;
import br.edu.udf.cs.sinal.chamado.domain.model.EstadoChamado;
import br.edu.udf.cs.sinal.chamado.domain.model.ModalidadeChamado;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.time.Instant;

// replay = true quando a mesma chave e os mesmos dados já tinham registrado este Chamado (Etapa 4 §17).
public record ChamadoRegistradoResultado(Long id, ModalidadeChamado modalidade, OrgaoSeguranca orgao,
                                         Long ocorrenciaId, Double latitude, Double longitude, Float precisao,
                                         Instant timestampLocalizacao, Boolean baixaPrecisao,
                                         String detalheAdicional, EstadoChamado estado, Instant dataCriacao,
                                         boolean replay) {

    static ChamadoRegistradoResultado de(Chamado chamado, boolean replay) {
        return new ChamadoRegistradoResultado(chamado.getId(), chamado.getModalidade(), chamado.getOrgao(),
                chamado.getOcorrenciaId(), chamado.getLocalizacao().getLatitude(),
                chamado.getLocalizacao().getLongitude(), chamado.getLocalizacao().getPrecisao(),
                chamado.getLocalizacao().getTimestamp(), chamado.getBaixaPrecisao(), chamado.getDetalheAdicional(),
                chamado.getEstado(), chamado.getDataCriacao(), replay);
    }

    // Coordenadas e detalhe (pode conter dados de saúde) nunca aparecem em logs (Etapa 4 §23).
    @Override
    public String toString() {
        return "ChamadoRegistradoResultado[id=" + id + ", modalidade=" + modalidade + ", orgao=" + orgao
                + ", estado=" + estado + ", replay=" + replay + "]";
    }
}
