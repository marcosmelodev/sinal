package br.edu.udf.cs.sinal.plataforma.persistencia;

import br.edu.udf.cs.sinal.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// Mesma configuração do contextLoads, para reaproveitar o contexto e o container MySQL.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MigracoesFlywayTest {

    private static final List<String> TABELAS = List.of(
            "usuario", "ocorrencia_tipo", "chamado", "chamado_transicao", "encaminhamento", "contato_confianca");

    private static final Timestamp AGORA = Timestamp.from(Instant.parse("2026-10-15T12:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    private long inserirUsuario(String cpf, String email) {
        jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                "Maria", cpf, email, "hash", true, AGORA);
        return jdbc.queryForObject("SELECT id FROM usuario WHERE cpf = ?", Long.class, cpf);
    }

    private long inserirChamado(long usuarioId, String chave) {
        jdbc.update("""
                INSERT INTO chamado (usuario_id, modalidade, orgao, ocorrencia_id, latitude, longitude, precisao,
                    timestamp_localizacao, baixa_precisao, detalhe_adicional, estado, data_criacao, chave_idempotencia)
                VALUES (?, 'EMERGENCIA_IMEDIATA', 'POLICIA_MILITAR', NULL, -15.79, -47.88, 25.0, ?, FALSE, NULL,
                    'CRIADO', ?, ?)""", usuarioId, AGORA, AGORA, chave);
        return jdbc.queryForObject("SELECT id FROM chamado WHERE usuario_id = ? AND chave_idempotencia = ?",
                Long.class, usuarioId, chave);
    }

    private void inserirEncaminhamento(long chamadoId) {
        jdbc.update("""
                INSERT INTO encaminhamento (chamado_id, modo, status, data_criacao, proxima_transicao_em)
                VALUES (?, 'SIMULADO', 'AGENDADO', ?, ?)""", chamadoId, AGORA, AGORA);
    }

    @Nested
    class Migracoes {

        @Test
        void deveAplicarV1EV2ComSucesso() {
            List<Map<String, Object>> historico = jdbc.queryForList(
                    "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

            assertThat(historico).extracting(l -> l.get("version"), l -> l.get("success"))
                    .containsExactly(tuple("1", true), tuple("2", true));
        }

        @Test
        void deveCriarAsTabelasDoNucleoComInnoDBEUtf8mb4() {
            List<Map<String, Object>> tabelas = jdbc.queryForList("""
                    SELECT table_name AS nome, engine, table_collation AS collation
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'""");

            assertThat(tabelas).extracting(t -> t.get("nome")).containsExactlyInAnyOrderElementsOf(TABELAS);
            assertThat(tabelas).allSatisfy(t -> {
                assertThat(t.get("engine")).isEqualTo("InnoDB");
                assertThat((String) t.get("collation")).startsWith("utf8mb4");
            });
        }

        @Test
        void naoDeveCriarTabelasDeEtapasFuturas() {
            Integer quantidade = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = DATABASE() AND table_name IN ('refresh_token', 'auditoria_evento')""",
                    Integer.class);

            assertThat(quantidade).isZero();
        }

        @Test
        void deveCriarOsIndicesAprovados() {
            List<String> indices = jdbc.queryForList("""
                    SELECT DISTINCT index_name FROM information_schema.statistics
                    WHERE table_schema = DATABASE()""", String.class);

            assertThat(indices).contains(
                    "idx_ocorrencia_tipo_orgao_ativo_ordem", "idx_chamado_historico",
                    "idx_chamado_transicao_chamado_ocorrido", "idx_encaminhamento_status_proxima",
                    "idx_contato_confianca_usuario", "uk_usuario_cpf", "uk_usuario_email",
                    "uk_ocorrencia_tipo_codigo", "uk_chamado_usuario_chave_idempotencia", "uk_encaminhamento_chamado");
        }

        @Test
        void indiceDoHistoricoDeveOrdenarDataDeCriacaoDecrescente() {
            List<Map<String, Object>> colunas = jdbc.queryForList("""
                    SELECT column_name AS coluna, collation AS ordem FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND index_name = 'idx_chamado_historico'
                    ORDER BY seq_in_index""");

            assertThat(colunas).extracting(c -> c.get("coluna"), c -> c.get("ordem"))
                    .containsExactly(tuple("usuario_id", "A"), tuple("data_criacao", "D"), tuple("id", "A"));
        }

        @Test
        void deveCriarAsChavesEstrangeirasAprovadas() {
            List<Map<String, Object>> chaves = jdbc.queryForList("""
                    SELECT table_name AS tabela, column_name AS coluna, referenced_table_name AS referencia
                    FROM information_schema.key_column_usage
                    WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL""");

            assertThat(chaves).extracting(c -> c.get("tabela"), c -> c.get("coluna"), c -> c.get("referencia"))
                    .containsExactlyInAnyOrder(
                            tuple("chamado", "usuario_id", "usuario"),
                            tuple("chamado", "ocorrencia_id", "ocorrencia_tipo"),
                            tuple("chamado_transicao", "chamado_id", "chamado"),
                            tuple("encaminhamento", "chamado_id", "chamado"),
                            tuple("contato_confianca", "usuario_id", "usuario"));
        }
    }

    @Nested
    class SeedDeOcorrencias {

        @Test
        void deveConterExatamenteAs33OcorrenciasAprovadas() {
            List<Map<String, Object>> ocorrencias = jdbc.queryForList(
                    "SELECT codigo, orgao, nome, ordem FROM ocorrencia_tipo");

            assertThat(ocorrencias)
                    .extracting(o -> o.get("codigo"), o -> o.get("orgao"), o -> o.get("nome"), o -> o.get("ordem"))
                    .containsExactlyInAnyOrder(
                            tuple("PM_ASSALTO_ROUBO", "POLICIA_MILITAR", "Assalto / Roubo em andamento", 1),
                            tuple("PM_AGRESSAO_BRIGA", "POLICIA_MILITAR", "Agressão física / Briga", 2),
                            tuple("PM_VIOLENCIA_DOMESTICA", "POLICIA_MILITAR", "Violência doméstica / Pessoa em perigo", 3),
                            tuple("PM_INVASAO_DOMICILIO", "POLICIA_MILITAR", "Invasão de domicílio / Estabelecimento", 4),
                            tuple("PM_AMEACA_ARMA_FOGO", "POLICIA_MILITAR", "Ameaça de morte / Arma de fogo", 5),
                            tuple("PM_FURTO_VEICULO_OBJETO", "POLICIA_MILITAR", "Furto de veículo / Objeto", 6),
                            tuple("PM_DISPAROS_ARMA_FOGO", "POLICIA_MILITAR", "Disparos de arma de fogo", 7),
                            tuple("PM_PESSOA_SUSPEITA", "POLICIA_MILITAR", "Pessoa suspeita rondando", 8),
                            tuple("PM_DESORDEM_PERTURBACAO", "POLICIA_MILITAR", "Desordem / Perturbação do sossego", 9),
                            tuple("PM_ACIDENTE_TRANSITO_BRIGA_FUGA", "POLICIA_MILITAR", "Acidente de trânsito com briga / Fuga", 10),
                            tuple("PM_OUTRO", "POLICIA_MILITAR", "Outro / Não sei informar", 99),
                            tuple("SAMU_DESACORDADO_NAO_RESPIRA", "SAMU", "Pessoa desacordada / Não respira", 1),
                            tuple("SAMU_DOR_PEITO_INFARTO", "SAMU", "Dor forte no peito / Suspeita de infarto", 2),
                            tuple("SAMU_FALTA_AR_ENGASGO", "SAMU", "Falta de ar grave / Engasgo", 3),
                            tuple("SAMU_CONVULSAO", "SAMU", "Convulsão / Ataque epiléptico", 4),
                            tuple("SAMU_SANGRAMENTO_GRAVE", "SAMU", "Sangramento grave / Hemorragia", 5),
                            tuple("SAMU_QUEDA_FRATURA", "SAMU", "Queda de altura com fratura", 6),
                            tuple("SAMU_SUSPEITA_AVC", "SAMU", "Boca torta / Perda de força (Suspeita de AVC)", 7),
                            tuple("SAMU_QUEIMADURA_GRAVE", "SAMU", "Queimadura grave", 8),
                            tuple("SAMU_INTOXICACAO_ENVENENAMENTO", "SAMU", "Intoxicação / Envenenamento", 9),
                            tuple("SAMU_TENTATIVA_SUICIDIO", "SAMU", "Tentativa de suicídio", 10),
                            tuple("SAMU_OUTRO", "SAMU", "Outro / Não sei informar", 99),
                            tuple("CBM_INCENDIO_RESIDENCIA_PREDIO", "CORPO_DE_BOMBEIROS", "Incêndio em residência / Prédio", 1),
                            tuple("CBM_INCENDIO_MATA_VEGETACAO", "CORPO_DE_BOMBEIROS", "Incêndio em mata / Vegetação", 2),
                            tuple("CBM_ACIDENTE_PRESO_FERRAGENS", "CORPO_DE_BOMBEIROS", "Acidente com pessoa presa nas ferragens", 3),
                            tuple("CBM_VAZAMENTO_GAS", "CORPO_DE_BOMBEIROS", "Vazamento de gás com cheiro forte", 4),
                            tuple("CBM_AFOGAMENTO", "CORPO_DE_BOMBEIROS", "Afogamento / Vítima em rio ou piscina", 5),
                            tuple("CBM_DESABAMENTO", "CORPO_DE_BOMBEIROS", "Desabamento / Risco de queda de estrutura", 6),
                            tuple("CBM_CHOQUE_ELETRICO", "CORPO_DE_BOMBEIROS", "Choque elétrico / Fiação caída na rua", 7),
                            tuple("CBM_QUEDA_ARVORE", "CORPO_DE_BOMBEIROS", "Queda de árvore sobre casa ou via", 8),
                            tuple("CBM_ATAQUE_ANIMAIS", "CORPO_DE_BOMBEIROS", "Ataque de animais perigosos", 9),
                            tuple("CBM_PESSOA_ANIMAL_PRESO", "CORPO_DE_BOMBEIROS", "Pessoa ou animal preso em local de risco", 10),
                            tuple("CBM_OUTRO", "CORPO_DE_BOMBEIROS", "Outro / Não sei informar", 99));
        }

        @Test
        void deveTerOnzeOcorrenciasAtivasPorOrgao() {
            List<Map<String, Object>> porOrgao = jdbc.queryForList(
                    "SELECT orgao, COUNT(*) AS total, SUM(ativo) AS ativos FROM ocorrencia_tipo GROUP BY orgao");

            assertThat(porOrgao).extracting(o -> o.get("orgao"), o -> ((Number) o.get("total")).intValue(),
                            o -> ((Number) o.get("ativos")).intValue())
                    .containsExactlyInAnyOrder(
                            tuple("POLICIA_MILITAR", 11, 11),
                            tuple("SAMU", 11, 11),
                            tuple("CORPO_DE_BOMBEIROS", 11, 11));
        }

        @Test
        void deveTerExatamenteUmItemGenericoPorOrgaoNaUltimaPosicao() {
            List<Map<String, Object>> genericos = jdbc.queryForList(
                    "SELECT orgao, ordem FROM ocorrencia_tipo WHERE nome = 'Outro / Não sei informar'");

            assertThat(genericos).extracting(g -> g.get("orgao"), g -> g.get("ordem"))
                    .containsExactlyInAnyOrder(
                            tuple("POLICIA_MILITAR", 99), tuple("SAMU", 99), tuple("CORPO_DE_BOMBEIROS", 99));
        }

        @Test
        void naoDeveConterOTextoAntigoDaPm() {
            Integer quantidade = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ocorrencia_tipo WHERE nome LIKE '%Mulher em perigo%'", Integer.class);

            assertThat(quantidade).isZero();
        }

        @Test
        void deveRejeitarCodigoDuplicado() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO ocorrencia_tipo (codigo, orgao, nome, ordem, ativo) VALUES ('PM_OUTRO', 'SAMU', 'X', 1, TRUE)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Restricoes {

        @Test
        void deveRejeitarCpfDuplicado() {
            inserirUsuario("52998224725", "a@exemplo.com");

            assertThatThrownBy(() -> inserirUsuario("52998224725", "b@exemplo.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarEmailDuplicado() {
            inserirUsuario("52998224725", "a@exemplo.com");

            assertThatThrownBy(() -> inserirUsuario("11144477735", "a@exemplo.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarChaveDeIdempotenciaRepetidaParaOMesmoUsuario() {
            long usuarioId = inserirUsuario("52998224725", "a@exemplo.com");
            inserirChamado(usuarioId, "chave-1");

            assertThatThrownBy(() -> inserirChamado(usuarioId, "chave-1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveAceitarMesmaChaveDeIdempotenciaParaUsuariosDiferentes() {
            long usuarioA = inserirUsuario("52998224725", "a@exemplo.com");
            long usuarioB = inserirUsuario("11144477735", "b@exemplo.com");

            inserirChamado(usuarioA, "chave-1");
            inserirChamado(usuarioB, "chave-1");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chamado", Integer.class)).isEqualTo(2);
        }

        @Test
        void deveRejeitarChamadoSemChaveDeIdempotencia() {
            long usuarioId = inserirUsuario("52998224725", "a@exemplo.com");

            assertThatThrownBy(() -> inserirChamado(usuarioId, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarChamadoDeUsuarioInexistente() {
            assertThatThrownBy(() -> inserirChamado(999_999L, "chave-1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarSegundoEncaminhamentoParaOMesmoChamado() {
            long chamadoId = inserirChamado(inserirUsuario("52998224725", "a@exemplo.com"), "chave-1");
            inserirEncaminhamento(chamadoId);

            assertThatThrownBy(() -> inserirEncaminhamento(chamadoId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarEncaminhamentoDeChamadoInexistente() {
            assertThatThrownBy(() -> inserirEncaminhamento(999_999L))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarTransicaoDeChamadoInexistente() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO chamado_transicao (chamado_id, estado, ocorrido_em) VALUES (999999, 'CRIADO', ?)", AGORA))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void deveRejeitarContatoDeUsuarioInexistente() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO contato_confianca (usuario_id, nome, telefone) VALUES (999999, 'Ana', '61999998888')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void devePreservarPrecisaoDeMicrossegundosEmUtc() {
            Timestamp instante = Timestamp.from(Instant.parse("2026-10-15T12:34:56.123456Z"));
            jdbc.update("INSERT INTO usuario (nome, cpf, email, senha_hash, ativo, data_cadastro) VALUES (?, ?, ?, ?, ?, ?)",
                    "Maria", "52998224725", "a@exemplo.com", "hash", true, instante);

            Timestamp lido = jdbc.queryForObject("SELECT data_cadastro FROM usuario WHERE cpf = '52998224725'",
                    Timestamp.class);

            assertThat(lido.toInstant()).isEqualTo(instante.toInstant());
        }
    }
}
