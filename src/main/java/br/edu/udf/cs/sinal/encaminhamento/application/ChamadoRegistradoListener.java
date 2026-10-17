package br.edu.udf.cs.sinal.encaminhamento.application;

import br.edu.udf.cs.sinal.chamado.application.ChamadoRegistrado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// O módulo encaminhamento reage ao registro (Etapa 5 §17), após o commit, sem acoplar o módulo chamado.
// Síncrono no MVP; uma falha nunca afeta a resposta do registro: a reconciliação do job a recupera.
@Component
public class ChamadoRegistradoListener {

    private static final Logger LOG = LoggerFactory.getLogger(ChamadoRegistradoListener.class);

    private final EncaminhamentoService encaminhamentoService;

    public ChamadoRegistradoListener(EncaminhamentoService encaminhamentoService) {
        this.encaminhamentoService = encaminhamentoService;
    }

    // NOT_SUPPORTED: suspende a transação já confirmada; o serviço abre a própria transação.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aoRegistrarChamado(ChamadoRegistrado evento) {
        try {
            encaminhamentoService.iniciar(evento.chamadoId());
        } catch (RuntimeException e) {
            LOG.warn("Falha ao iniciar o encaminhamento simulado do chamado {} ({}); a reconciliação tentará novamente.",
                    evento.chamadoId(), e.getClass().getName());
        }
    }
}
