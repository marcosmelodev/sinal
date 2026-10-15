package br.edu.udf.cs.sinal.domain.repositories;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;

import java.util.List;
import java.util.Optional;

public interface OcorrenciaTipoRepository {

    List<OcorrenciaTipo> listarAtivasPorOrgaoOrdenadas(OrgaoSeguranca orgao);
    Optional<OcorrenciaTipo> buscarPorId(Long id);

}
