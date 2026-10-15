package br.edu.udf.cs.sinal.encaminhamento.domain.gateway;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

// ocorrenciaId é nulo na Emergência Imediata; centralCodigo é nulo quando a jurisdição não foi determinada.
public record SolicitacaoEncaminhamento(Long chamadoId, OrgaoSeguranca orgao, Long ocorrenciaId,
                                        Localizacao localizacao, String centralCodigo) {

    public SolicitacaoEncaminhamento {
        if (chamadoId == null) {
            throw new IllegalArgumentException("O chamadoId é obrigatório.");
        }
        if (orgao == null) {
            throw new IllegalArgumentException("O órgão é obrigatório.");
        }
        if (localizacao == null) {
            throw new IllegalArgumentException("A localização é obrigatória.");
        }
    }
}
