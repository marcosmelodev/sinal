package br.edu.udf.cs.sinal.chamado.infrastructure.catalogo;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// Usa o seed V2 (33 ocorrências); a transação de cada teste é desfeita.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogoDeOcorrenciasJdbc.class})
class CatalogoDeOcorrenciasJdbcTest {

    @Autowired
    private CatalogoDeOcorrenciasJdbc catalogo;

    @Autowired
    private JdbcTemplate jdbc;

    private Long idDe(String codigo) {
        return jdbc.queryForObject("SELECT id FROM ocorrencia_tipo WHERE codigo = ?", Long.class, codigo);
    }

    @Test
    void ocorrenciaAtivaDoProprioOrgaoDeveSerAceita() {
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(idDe("SAMU_CONVULSAO"), OrgaoSeguranca.SAMU)).isTrue();
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(idDe("PM_OUTRO"), OrgaoSeguranca.POLICIA_MILITAR)).isTrue();
    }

    @Test
    void ocorrenciaDeOutroOrgaoDeveSerRecusada() {
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(idDe("SAMU_CONVULSAO"), OrgaoSeguranca.CORPO_DE_BOMBEIROS)).isFalse();
    }

    @Test
    void ocorrenciaInativaDeveSerRecusada() {
        Long id = idDe("CBM_AFOGAMENTO");
        jdbc.update("UPDATE ocorrencia_tipo SET ativo = FALSE WHERE id = ?", id);

        assertThat(catalogo.ocorrenciaAtivaDoOrgao(id, OrgaoSeguranca.CORPO_DE_BOMBEIROS)).isFalse();
    }

    @Test
    void ocorrenciaInexistenteOuParametrosNulosDevemSerRecusados() {
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(999_999L, OrgaoSeguranca.SAMU)).isFalse();
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(null, OrgaoSeguranca.SAMU)).isFalse();
        assertThat(catalogo.ocorrenciaAtivaDoOrgao(idDe("SAMU_CONVULSAO"), null)).isFalse();
    }
}
