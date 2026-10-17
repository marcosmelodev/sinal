package br.edu.udf.cs.sinal.chamado.application;

import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

// Consulta ao catálogo de referência (Etapa 5 §10): sem modelo nem repositório de domínio.
public interface CatalogoDeOcorrencias {

    // Verdadeiro se a ocorrência existe, está ativa e pertence ao órgão informado.
    boolean ocorrenciaAtivaDoOrgao(Long ocorrenciaId, OrgaoSeguranca orgao);
}
