package br.edu.udf.cs.sinal.jurisdicao.domain.gateway;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.Jurisdicao;

import java.util.Optional;

public interface JurisdicaoGateway {

    // Vazio quando não há jurisdição para a localização; falhas técnicas são exceções não verificadas.
    Optional<Jurisdicao> consultar(Localizacao localizacao, OrgaoSeguranca orgao);
}
