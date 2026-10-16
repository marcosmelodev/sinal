package br.edu.udf.cs.sinal.chamado.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.compartilhado.domain.Localizacao;
import br.edu.udf.cs.sinal.compartilhado.domain.OrgaoSeguranca;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Chamado {

    public static final String USUARIO_OBRIGATORIO = "CHAMADO_USUARIO_OBRIGATORIO";
    public static final String LOCALIZACAO_OBRIGATORIA = "CHAMADO_LOCALIZACAO_OBRIGATORIA";
    public static final String ORGAO_OBRIGATORIO = "CHAMADO_ORGAO_OBRIGATORIO";
    public static final String OCORRENCIA_OBRIGATORIA = "CHAMADO_OCORRENCIA_OBRIGATORIA";
    public static final String DETALHE_EXCEDE_LIMITE = "CHAMADO_DETALHE_EXCEDE_LIMITE";
    public static final String TRANSICAO_INVALIDA = "CHAMADO_TRANSICAO_INVALIDA";
    public static final String INSTANTE_INVALIDO = "CHAMADO_INSTANTE_INVALIDO";

    public static final int LIMITE_DETALHE_ADICIONAL = 500;

    // CH7: única sequência válida do ciclo de vida.
    private static final List<EstadoChamado> SEQUENCIA_DE_ESTADOS = List.of(
            EstadoChamado.CRIADO, EstadoChamado.ENCAMINHADO_SIMULADO, EstadoChamado.ENCERRADO);

    // Atribuído pela persistência; nulo até o Chamado ser salvo.
    private final Long id;
    private final Long usuarioId;
    private final ModalidadeChamado modalidade;
    private final OrgaoSeguranca orgao;
    private final Long ocorrenciaId;
    private final Localizacao localizacao;
    private final Boolean baixaPrecisao;
    private final String detalheAdicional;
    private final Instant dataCriacao;
    private final List<Transicao> transicoes = new ArrayList<>();
    private EstadoChamado estado;

    private Chamado(Long usuarioId, ModalidadeChamado modalidade, OrgaoSeguranca orgao, Long ocorrenciaId,
                    Localizacao localizacao, String detalheAdicional, Float limiarBaixaPrecisao, Instant instante) {
        this.id = null;
        this.usuarioId = usuarioId;
        this.modalidade = modalidade;
        this.orgao = orgao;
        this.ocorrenciaId = ocorrenciaId;
        this.localizacao = localizacao;
        this.detalheAdicional = detalheAdicional;
        // CH5: a baixa precisão só é sinalizada; nunca bloqueia o registro.
        this.baixaPrecisao = localizacao.ehBaixaPrecisao(limiarBaixaPrecisao);
        this.dataCriacao = instante;
        // CH6
        this.estado = EstadoChamado.CRIADO;
        this.transicoes.add(new Transicao(EstadoChamado.CRIADO, instante));
    }

    // Reconstituição: baixaPrecisao é o valor gravado no registro (N2), nunca recalculado.
    private Chamado(Long id, Long usuarioId, ModalidadeChamado modalidade, OrgaoSeguranca orgao, Long ocorrenciaId,
                    Localizacao localizacao, Boolean baixaPrecisao, String detalheAdicional, EstadoChamado estado,
                    Instant dataCriacao, List<Transicao> transicoes) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.modalidade = modalidade;
        this.orgao = orgao;
        this.ocorrenciaId = ocorrenciaId;
        this.localizacao = localizacao;
        this.baixaPrecisao = baixaPrecisao;
        this.detalheAdicional = detalheAdicional;
        this.estado = estado;
        this.dataCriacao = dataCriacao;
        this.transicoes.addAll(transicoes);
    }

    public static Chamado registrar(Long usuarioId, OrgaoSeguranca orgao, Long ocorrenciaId, Localizacao localizacao,
                                    String detalheAdicional, Float limiarBaixaPrecisao, Instant instante) {
        validarUsuarioELocalizacao(usuarioId, localizacao);
        // CH3: a existência e o órgão da ocorrência são validados pela Application.
        if (orgao == null) {
            throw new DominioException(ORGAO_OBRIGATORIO, "O órgão de segurança é obrigatório.");
        }
        if (ocorrenciaId == null) {
            throw new DominioException(OCORRENCIA_OBRIGATORIA, "A ocorrência é obrigatória.");
        }
        String detalheNormalizado = normalizarDetalhe(detalheAdicional);
        validarInstanteInformado(instante);
        return new Chamado(usuarioId, ModalidadeChamado.PADRAO, orgao, ocorrenciaId, localizacao,
                detalheNormalizado, limiarBaixaPrecisao, instante);
    }

    // CH2: a Emergência Imediata vai sempre para a PM, sem ocorrência e sem detalhe.
    public static Chamado registrarEmergenciaImediata(Long usuarioId, Localizacao localizacao,
                                                      Float limiarBaixaPrecisao, Instant instante) {
        validarUsuarioELocalizacao(usuarioId, localizacao);
        validarInstanteInformado(instante);
        return new Chamado(usuarioId, ModalidadeChamado.EMERGENCIA_IMEDIATA, OrgaoSeguranca.POLICIA_MILITAR, null,
                localizacao, null, limiarBaixaPrecisao, instante);
    }

    // Usada pela persistência; aplica CH1 a CH8. Nulos estruturais e EI incoerente indicam dados corrompidos.
    public static Chamado reconstituir(Long id, Long usuarioId, ModalidadeChamado modalidade, OrgaoSeguranca orgao,
                                       Long ocorrenciaId, Localizacao localizacao, Boolean baixaPrecisao,
                                       String detalheAdicional, EstadoChamado estado, Instant dataCriacao,
                                       List<Transicao> transicoes) {
        if (id == null || modalidade == null || estado == null || baixaPrecisao == null
                || transicoes == null || transicoes.isEmpty() || transicoes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "id, modalidade, estado, baixaPrecisao e transições são obrigatórios na reconstituição.");
        }
        validarUsuarioELocalizacao(usuarioId, localizacao);
        validarInstanteInformado(dataCriacao);
        String detalheNormalizado;
        if (modalidade == ModalidadeChamado.EMERGENCIA_IMEDIATA) {
            if (orgao != OrgaoSeguranca.POLICIA_MILITAR || ocorrenciaId != null || detalheAdicional != null) {
                throw new IllegalArgumentException(
                        "Emergência Imediata reconstituída com órgão, ocorrência ou detalhe incoerentes.");
            }
            detalheNormalizado = null;
        } else {
            if (orgao == null) {
                throw new DominioException(ORGAO_OBRIGATORIO, "O órgão de segurança é obrigatório.");
            }
            if (ocorrenciaId == null) {
                throw new DominioException(OCORRENCIA_OBRIGATORIA, "A ocorrência é obrigatória.");
            }
            detalheNormalizado = normalizarDetalhe(detalheAdicional);
        }
        validarHistorico(transicoes, dataCriacao, estado);
        return new Chamado(id, usuarioId, modalidade, orgao, ocorrenciaId, localizacao, baixaPrecisao,
                detalheNormalizado, estado, dataCriacao, transicoes);
    }

    // CH6 a CH8: o histórico é um prefixo de CRIADO -> ENCAMINHADO_SIMULADO -> ENCERRADO, iniciado na criação,
    // com instantes não decrescentes e terminando no estado atual.
    private static void validarHistorico(List<Transicao> transicoes, Instant dataCriacao, EstadoChamado estado) {
        if (transicoes.size() > SEQUENCIA_DE_ESTADOS.size()) {
            throw historicoInvalido();
        }
        for (int i = 0; i < transicoes.size(); i++) {
            if (transicoes.get(i).getEstado() != SEQUENCIA_DE_ESTADOS.get(i)) {
                throw historicoInvalido();
            }
            if (i > 0 && transicoes.get(i).getOcorridoEm().isBefore(transicoes.get(i - 1).getOcorridoEm())) {
                throw new DominioException(INSTANTE_INVALIDO,
                        "Os instantes das transições não podem diminuir.");
            }
        }
        if (!transicoes.get(0).getOcorridoEm().equals(dataCriacao)
                || transicoes.get(transicoes.size() - 1).getEstado() != estado) {
            throw historicoInvalido();
        }
    }

    private static DominioException historicoInvalido() {
        return new DominioException(TRANSICAO_INVALIDA, "Histórico de transições inconsistente.");
    }

    public void encaminharSimulado(Instant instante) {
        transicionar(EstadoChamado.CRIADO, EstadoChamado.ENCAMINHADO_SIMULADO, instante);
    }

    public void encerrar(Instant instante) {
        transicionar(EstadoChamado.ENCAMINHADO_SIMULADO, EstadoChamado.ENCERRADO, instante);
    }

    // CH7: única sequência válida é CRIADO -> ENCAMINHADO_SIMULADO -> ENCERRADO.
    // CH8: o estado acompanha a última transição e os instantes não diminuem.
    private void transicionar(EstadoChamado origem, EstadoChamado destino, Instant instante) {
        if (estado != origem) {
            throw new DominioException(TRANSICAO_INVALIDA,
                    "Transição inválida de " + estado + " para " + destino + ".");
        }
        validarInstanteInformado(instante);
        if (instante.isBefore(ultimaTransicao().getOcorridoEm())) {
            throw new DominioException(INSTANTE_INVALIDO,
                    "O instante da transição não pode ser anterior ao da última transição.");
        }
        transicoes.add(new Transicao(destino, instante));
        estado = destino;
    }

    // CH1
    private static void validarUsuarioELocalizacao(Long usuarioId, Localizacao localizacao) {
        if (usuarioId == null) {
            throw new DominioException(USUARIO_OBRIGATORIO, "O usuário é obrigatório.");
        }
        if (localizacao == null) {
            throw new DominioException(LOCALIZACAO_OBRIGATORIA, "A localização é obrigatória.");
        }
    }

    private static void validarInstanteInformado(Instant instante) {
        if (instante == null) {
            throw new DominioException(INSTANTE_INVALIDO, "O instante é obrigatório.");
        }
    }

    // CH4: opcional; aparado; vazio vira null; máximo de 500 caracteres.
    private static String normalizarDetalhe(String detalheAdicional) {
        if (detalheAdicional == null) {
            return null;
        }
        String aparado = detalheAdicional.trim();
        if (aparado.isEmpty()) {
            return null;
        }
        if (aparado.length() > LIMITE_DETALHE_ADICIONAL) {
            throw new DominioException(DETALHE_EXCEDE_LIMITE,
                    "O detalhe adicional deve ter no máximo " + LIMITE_DETALHE_ADICIONAL + " caracteres.");
        }
        return aparado;
    }

    private Transicao ultimaTransicao() {
        return transicoes.get(transicoes.size() - 1);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public ModalidadeChamado getModalidade() {
        return modalidade;
    }

    public OrgaoSeguranca getOrgao() {
        return orgao;
    }

    public Long getOcorrenciaId() {
        return ocorrenciaId;
    }

    public Localizacao getLocalizacao() {
        return localizacao;
    }

    public Boolean getBaixaPrecisao() {
        return baixaPrecisao;
    }

    public String getDetalheAdicional() {
        return detalheAdicional;
    }

    public EstadoChamado getEstado() {
        return estado;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public List<Transicao> getTransicoes() {
        return List.copyOf(transicoes);
    }
}
