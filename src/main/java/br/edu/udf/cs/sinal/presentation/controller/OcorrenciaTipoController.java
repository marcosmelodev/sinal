package br.edu.udf.cs.sinal.presentation.controller;

import br.edu.udf.cs.sinal.application.usecase.ListarOcorrenciasPorOrgaoUseCase;
import br.edu.udf.cs.sinal.domain.enums.OrgaoSeguranca;
import br.edu.udf.cs.sinal.presentation.dto.response.OcorrenciaTipoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ocorrencias")
public class OcorrenciaTipoController {

    private final ListarOcorrenciasPorOrgaoUseCase listarOcorrenciasUseCase;

    public OcorrenciaTipoController(ListarOcorrenciasPorOrgaoUseCase listarOcorrenciasUseCase) {
        this.listarOcorrenciasUseCase = listarOcorrenciasUseCase;
    }

    @GetMapping
    public ResponseEntity<List<OcorrenciaTipoResponse>> listarPorOrgao(@RequestParam("orgao") OrgaoSeguranca orgao) {
        List<OcorrenciaTipoResponse> response = listarOcorrenciasUseCase.executar(orgao)
                .stream()
                .map(OcorrenciaTipoResponse::fromDomain)
                .toList();

        return ResponseEntity.ok(response);
    }
}
