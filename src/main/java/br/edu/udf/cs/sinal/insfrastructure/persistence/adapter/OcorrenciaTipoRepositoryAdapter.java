package br.edu.udf.cs.sinal.insfrastructure.persistence.adapter;

import br.edu.udf.cs.sinal.domain.enums.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;
import br.edu.udf.cs.sinal.domain.repositories.OcorrenciaTipoRepository;
import br.edu.udf.cs.sinal.insfrastructure.persistence.OcorrenciaTipoEntity;
import br.edu.udf.cs.sinal.insfrastructure.persistence.repository.SpringDataOcorrenciaTipoRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OcorrenciaTipoRepositoryAdapter implements OcorrenciaTipoRepository {

    private final SpringDataOcorrenciaTipoRepository springDataRepository;

    public OcorrenciaTipoRepositoryAdapter(SpringDataOcorrenciaTipoRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public List<OcorrenciaTipo> listarAtivasPorOrgaoOrdenadas(OrgaoSeguranca orgao) {
        return springDataRepository.findByOrgaoAndAtivoTrueOrderByOrdemAsc(orgao)
                .stream()
                .map(OcorrenciaTipoEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<OcorrenciaTipo> buscarPorId(Long id) {
        return springDataRepository.findById(id).map(OcorrenciaTipoEntity::toDomain);
    }
}
