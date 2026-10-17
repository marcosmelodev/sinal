package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.jurisdicao.domain.gateway.JurisdicaoGateway;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.CentralAtendimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Orquestra a consulta ao JurisdicaoGateway; a escolha da central pertence a Jurisdicao.centralPara (Etapa 5 §12).
// Falha técnica ou ausência de jurisdição não impedem o encaminhamento simulado (Etapa 5 §11).
@Service
public class DeterminacaoDeCentralService {

    private static final Logger LOG = LoggerFactory.getLogger(DeterminacaoDeCentralService.class);

    private final JurisdicaoGateway jurisdicaoGateway;

    public DeterminacaoDeCentralService(JurisdicaoGateway jurisdicaoGateway) {
        this.jurisdicaoGateway = jurisdicaoGateway;
    }

    public CentralDeterminada determinar(Localizacao localizacao, OrgaoSeguranca orgao) {
        try {
            return jurisdicaoGateway.consultar(localizacao, orgao)
                    .map(jurisdicao -> new CentralDeterminada(jurisdicao.getCodigo(),
                            jurisdicao.centralPara(orgao).map(CentralAtendimento::getCodigo).orElse(null)))
                    .orElse(CentralDeterminada.NENHUMA);
        } catch (RuntimeException e) {
            // Somente o tipo: mensagens podem conter dados da localização (Etapa 4 §23).
            LOG.warn("Falha ao consultar a jurisdição: {}. Encaminhamento seguirá sem central.", e.getClass().getName());
            return CentralDeterminada.NENHUMA;
        }
    }
}
