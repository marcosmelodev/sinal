package br.edu.udf.cs.sinal.presentation.dto.response;

import br.edu.udf.cs.sinal.domain.enums.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;

public record OcorrenciaTipoResponse(
        Long id,
        String codigo,
        OrgaoSeguranca orgao,
        String nome,
        Integer ordem
) {
    public static OcorrenciaTipoResponse fromDomain(OcorrenciaTipo model) {
        return new OcorrenciaTipoResponse(
                model.getId(),
                model.getCodigo(),
                model.getOrgao(),
                model.getNome(),
                model.getOrdem()
        );
    }
}
