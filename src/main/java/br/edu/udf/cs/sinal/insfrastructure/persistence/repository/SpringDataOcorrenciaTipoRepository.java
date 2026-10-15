package br.edu.udf.cs.sinal.insfrastructure.persistence.repository;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;
import br.edu.udf.cs.sinal.insfrastructure.persistence.OcorrenciaTipoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataOcorrenciaTipoRepository extends JpaRepository<OcorrenciaTipoEntity, Long> {
    List<OcorrenciaTipoEntity> findByOrgaoAndAtivoTrueOrderByOrdemAsc(OrgaoSeguranca orgao);
}
