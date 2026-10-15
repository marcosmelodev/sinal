package br.edu.udf.cs.sinal.application.usecase;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.domain.model.OcorrenciaTipo;
import br.edu.udf.cs.sinal.domain.repositories.OcorrenciaTipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListarOcorrenciasPorOrgaoUseCase {

    private final OcorrenciaTipoRepository repository;

    public ListarOcorrenciasPorOrgaoUseCase(OcorrenciaTipoRepository ocorrenciaTipoRepository){
        this.repository = ocorrenciaTipoRepository;
    }

    @Transactional(readOnly = true)
    public List<OcorrenciaTipo> executar(OrgaoSeguranca orgaoSeguranca){
        if (orgaoSeguranca == null){
            throw new IllegalArgumentException("O orgão de segurança deve ser informado.");
        }
        return repository.listarAtivasPorOrgaoOrdenadas(orgaoSeguranca);
    }

}
