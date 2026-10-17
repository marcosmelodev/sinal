package br.edu.udf.cs.sinal.chamado.infrastructure.catalogo;

import br.edu.udf.cs.sinal.chamado.application.CatalogoDeOcorrencias;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogoDeOcorrenciasJdbc implements CatalogoDeOcorrencias {

    private final JdbcTemplate jdbc;

    public CatalogoDeOcorrenciasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean ocorrenciaAtivaDoOrgao(Long ocorrenciaId, OrgaoSeguranca orgao) {
        if (ocorrenciaId == null || orgao == null) {
            return false;
        }
        Integer encontradas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ocorrencia_tipo WHERE id = ? AND orgao = ? AND ativo = TRUE",
                Integer.class, ocorrenciaId, orgao.name());
        return encontradas != null && encontradas > 0;
    }
}
