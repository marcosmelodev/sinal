-- Schema inicial do núcleo de domínio (Etapa 4 §12).
-- Instantes em UTC como DATETIME(6); enums persistidos por nome (VARCHAR).
-- refresh_token e auditoria_evento serão criadas nas etapas de segurança e auditoria.

CREATE TABLE usuario (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(255) NOT NULL,
    cpf           CHAR(11)     NOT NULL,
    email         VARCHAR(254) NOT NULL,
    senha_hash    VARCHAR(255) NOT NULL,
    ativo         BOOLEAN      NOT NULL,
    data_cadastro DATETIME(6)  NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf),
    CONSTRAINT uk_usuario_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ocorrencia_tipo (
    id     BIGINT       NOT NULL AUTO_INCREMENT,
    codigo VARCHAR(50)  NOT NULL,
    orgao  VARCHAR(30)  NOT NULL,
    nome   VARCHAR(120) NOT NULL,
    ordem  INT          NOT NULL,
    ativo  BOOLEAN      NOT NULL,
    CONSTRAINT pk_ocorrencia_tipo PRIMARY KEY (id),
    CONSTRAINT uk_ocorrencia_tipo_codigo UNIQUE (codigo),
    INDEX idx_ocorrencia_tipo_orgao_ativo_ordem (orgao, ativo, ordem)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE chamado (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id            BIGINT       NOT NULL,
    modalidade            VARCHAR(30)  NOT NULL,
    orgao                 VARCHAR(30)  NOT NULL,
    ocorrencia_id         BIGINT       NULL,
    latitude              DOUBLE       NOT NULL,
    longitude             DOUBLE       NOT NULL,
    precisao              FLOAT        NOT NULL,
    timestamp_localizacao DATETIME(6)  NOT NULL,
    baixa_precisao        BOOLEAN      NOT NULL,
    detalhe_adicional     VARCHAR(500) NULL,
    estado                VARCHAR(30)  NOT NULL,
    data_criacao          DATETIME(6)  NOT NULL,
    chave_idempotencia    VARCHAR(100) NOT NULL,
    CONSTRAINT pk_chamado PRIMARY KEY (id),
    CONSTRAINT uk_chamado_usuario_chave_idempotencia UNIQUE (usuario_id, chave_idempotencia),
    INDEX idx_chamado_historico (usuario_id, data_criacao DESC, id),
    CONSTRAINT fk_chamado_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_chamado_ocorrencia_tipo FOREIGN KEY (ocorrencia_id) REFERENCES ocorrencia_tipo (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Histórico append-only de estados do Chamado.
CREATE TABLE chamado_transicao (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    chamado_id  BIGINT      NOT NULL,
    estado      VARCHAR(30) NOT NULL,
    ocorrido_em DATETIME(6) NOT NULL,
    CONSTRAINT pk_chamado_transicao PRIMARY KEY (id),
    INDEX idx_chamado_transicao_chamado_ocorrido (chamado_id, ocorrido_em),
    CONSTRAINT fk_chamado_transicao_chamado FOREIGN KEY (chamado_id) REFERENCES chamado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- EN1: no máximo um Encaminhamento por Chamado.
CREATE TABLE encaminhamento (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    chamado_id           BIGINT       NOT NULL,
    modo                 VARCHAR(30)  NOT NULL,
    status               VARCHAR(30)  NOT NULL,
    jurisdicao_codigo    VARCHAR(100) NULL,
    central_codigo       VARCHAR(100) NULL,
    data_criacao         DATETIME(6)  NOT NULL,
    proxima_transicao_em DATETIME(6)  NULL,
    CONSTRAINT pk_encaminhamento PRIMARY KEY (id),
    CONSTRAINT uk_encaminhamento_chamado UNIQUE (chamado_id),
    INDEX idx_encaminhamento_status_proxima (status, proxima_transicao_em),
    CONSTRAINT fk_encaminhamento_chamado FOREIGN KEY (chamado_id) REFERENCES chamado (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE contato_confianca (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT       NOT NULL,
    nome       VARCHAR(255) NOT NULL,
    telefone   VARCHAR(11)  NOT NULL,
    CONSTRAINT pk_contato_confianca PRIMARY KEY (id),
    INDEX idx_contato_confianca_usuario (usuario_id),
    CONSTRAINT fk_contato_confianca_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
