package br.edu.udf.cs.sinal.jurisdicao.infrastructure;

import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;
import br.edu.udf.cs.sinal.jurisdicao.domain.gateway.JurisdicaoGateway;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.CentralAtendimento;
import br.edu.udf.cs.sinal.jurisdicao.domain.model.Jurisdicao;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// MVP (Etapa 4 §19): jurisdição e centrais EXPLICITAMENTE FICTÍCIAS; nenhuma integração oficial é inventada.
// Dentro de uma área aproximada do Distrito Federal devolve a jurisdição fictícia; fora dela, nenhuma
// (exercita o caminho de Encaminhamento sem central, Etapa 5 §11). A fonte real substituirá apenas este adapter.
@Component
public class JurisdicaoMockAdapter implements JurisdicaoGateway {

    static final double LATITUDE_MINIMA = -16.06;
    static final double LATITUDE_MAXIMA = -15.50;
    static final double LONGITUDE_MINIMA = -48.29;
    static final double LONGITUDE_MAXIMA = -47.31;

    static final Jurisdicao JURISDICAO_FICTICIA_DF = new Jurisdicao(
            "JURISDICAO-FICTICIA-DF", "Jurisdição fictícia – Distrito Federal (simulação)", List.of(
            new CentralAtendimento("CENTRAL-FICTICIA-DF-PM", "Central fictícia da Polícia Militar – DF (simulação)",
                    OrgaoSeguranca.POLICIA_MILITAR),
            new CentralAtendimento("CENTRAL-FICTICIA-DF-CBM",
                    "Central fictícia do Corpo de Bombeiros – DF (simulação)", OrgaoSeguranca.CORPO_DE_BOMBEIROS),
            new CentralAtendimento("CENTRAL-FICTICIA-DF-SAMU", "Central fictícia do SAMU – DF (simulação)",
                    OrgaoSeguranca.SAMU)));

    // O recorte é territorial: o órgão não altera a jurisdição; a central é escolhida por Jurisdicao.centralPara.
    @Override
    public Optional<Jurisdicao> consultar(Localizacao localizacao, OrgaoSeguranca orgao) {
        if (localizacao == null) {
            return Optional.empty();
        }
        boolean dentroDoDf = localizacao.getLatitude() >= LATITUDE_MINIMA && localizacao.getLatitude() <= LATITUDE_MAXIMA
                && localizacao.getLongitude() >= LONGITUDE_MINIMA && localizacao.getLongitude() <= LONGITUDE_MAXIMA;
        return dentroDoDf ? Optional.of(JURISDICAO_FICTICIA_DF) : Optional.empty();
    }
}
