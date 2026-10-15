package br.edu.udf.cs.sinal.encaminhamento.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;

import java.time.Instant;

// Processamento técnico do encaminhamento simulado; não representa atendimento por órgão real (EN4).
public class Encaminhamento {

    public static final String CHAMADO_OBRIGATORIO = "ENCAMINHAMENTO_CHAMADO_OBRIGATORIO";
    public static final String INSTANTE_INVALIDO = "ENCAMINHAMENTO_INSTANTE_INVALIDO";
    public static final String AGENDAMENTO_INVALIDO = "ENCAMINHAMENTO_AGENDAMENTO_INVALIDO";
    public static final String TRANSICAO_INVALIDA = "ENCAMINHAMENTO_TRANSICAO_INVALIDA";

    // Atribuído pela persistência; nulo até o Encaminhamento ser salvo.
    private final Long id;
    private final Long chamadoId;
    private final ModoEncaminhamento modo;
    private final String jurisdicaoCodigo;
    private final String centralCodigo;
    private final Instant dataCriacao;
    private StatusEncaminhamento status;
    private Instant proximaTransicaoEm;

    private Encaminhamento(Long id, Long chamadoId, ModoEncaminhamento modo, StatusEncaminhamento status,
                           String jurisdicaoCodigo, String centralCodigo, Instant dataCriacao,
                           Instant proximaTransicaoEm) {
        this.id = id;
        this.chamadoId = chamadoId;
        this.modo = modo;
        this.status = status;
        this.jurisdicaoCodigo = codigoOpcional(jurisdicaoCodigo);
        this.centralCodigo = codigoOpcional(centralCodigo);
        this.dataCriacao = dataCriacao;
        this.proximaTransicaoEm = proximaTransicaoEm;
    }

    // EN2: o modo é sempre SIMULADO. Jurisdição e central podem ser nulas se não forem determinadas (§11).
    public static Encaminhamento agendar(Long chamadoId, String jurisdicaoCodigo, String centralCodigo,
                                         Instant dataCriacao, Instant proximaTransicaoEm) {
        validarChamadoEDataCriacao(chamadoId, dataCriacao);
        validarAgendamento(proximaTransicaoEm, dataCriacao);
        return new Encaminhamento(null, chamadoId, ModoEncaminhamento.SIMULADO, StatusEncaminhamento.AGENDADO,
                jurisdicaoCodigo, centralCodigo, dataCriacao, proximaTransicaoEm);
    }

    // Usada pela persistência; aplica as mesmas invariantes, e nulos estruturais indicam dados corrompidos.
    public static Encaminhamento reconstituir(Long id, Long chamadoId, ModoEncaminhamento modo,
                                              StatusEncaminhamento status, String jurisdicaoCodigo,
                                              String centralCodigo, Instant dataCriacao,
                                              Instant proximaTransicaoEm) {
        if (id == null || modo == null || status == null) {
            throw new IllegalArgumentException("id, modo e status são obrigatórios na reconstituição.");
        }
        validarChamadoEDataCriacao(chamadoId, dataCriacao);
        if (status == StatusEncaminhamento.AGENDADO) {
            validarAgendamento(proximaTransicaoEm, dataCriacao);
        } else if (proximaTransicaoEm != null) {
            throw new DominioException(AGENDAMENTO_INVALIDO, "Encaminhamento concluído não possui agendamento.");
        }
        return new Encaminhamento(id, chamadoId, modo, status, jurisdicaoCodigo, centralCodigo, dataCriacao,
                proximaTransicaoEm);
    }

    // EN3: permanece AGENDADO com o novo horário.
    public void reagendar(Instant proximaTransicaoEm) {
        exigirAgendado();
        validarAgendamento(proximaTransicaoEm, dataCriacao);
        this.proximaTransicaoEm = proximaTransicaoEm;
    }

    // EN3: CONCLUIDO é terminal e não possui agendamento.
    public void concluir() {
        exigirAgendado();
        this.status = StatusEncaminhamento.CONCLUIDO;
        this.proximaTransicaoEm = null;
    }

    private void exigirAgendado() {
        if (status != StatusEncaminhamento.AGENDADO) {
            throw new DominioException(TRANSICAO_INVALIDA, "O encaminhamento já foi concluído.");
        }
    }

    private static void validarChamadoEDataCriacao(Long chamadoId, Instant dataCriacao) {
        if (chamadoId == null) {
            throw new DominioException(CHAMADO_OBRIGATORIO, "O chamado é obrigatório.");
        }
        if (dataCriacao == null) {
            throw new DominioException(INSTANTE_INVALIDO, "A data de criação é obrigatória.");
        }
    }

    // EN3: AGENDADO exige proximaTransicaoEm, que não pode ser anterior à criação.
    private static void validarAgendamento(Instant proximaTransicaoEm, Instant dataCriacao) {
        if (proximaTransicaoEm == null || proximaTransicaoEm.isBefore(dataCriacao)) {
            throw new DominioException(AGENDAMENTO_INVALIDO,
                    "A próxima transição é obrigatória e não pode ser anterior à criação.");
        }
    }

    private static String codigoOpcional(String codigo) {
        return codigo == null || codigo.isBlank() ? null : codigo.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getChamadoId() {
        return chamadoId;
    }

    public ModoEncaminhamento getModo() {
        return modo;
    }

    public StatusEncaminhamento getStatus() {
        return status;
    }

    public String getJurisdicaoCodigo() {
        return jurisdicaoCodigo;
    }

    public String getCentralCodigo() {
        return centralCodigo;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getProximaTransicaoEm() {
        return proximaTransicaoEm;
    }
}
