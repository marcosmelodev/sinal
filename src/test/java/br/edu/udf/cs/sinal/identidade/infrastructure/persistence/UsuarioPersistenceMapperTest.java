package br.edu.udf.cs.sinal.identidade.infrastructure.persistence;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioPersistenceMapperTest {

    private static final String NOME = "Maria da Silva";
    private static final String CPF = "52998224725";
    private static final String EMAIL = "maria@exemplo.com.br";
    private static final String SENHA_HASH = "$2a$10$hash";
    private static final Instant INSTANTE = Instant.parse("2026-10-15T12:34:56.123456Z");

    @Test
    void usuarioNovoDeveGerarEntidadeSemId() {
        UsuarioJpaEntity entity = UsuarioPersistenceMapper.toJpaEntity(
                Usuario.cadastrar(NOME, CPF, EMAIL, SENHA_HASH, INSTANTE));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getNome()).isEqualTo(NOME);
        assertThat(entity.getCpf()).isEqualTo(CPF);
        assertThat(entity.getEmail()).isEqualTo(EMAIL);
        assertThat(entity.getSenhaHash()).isEqualTo(SENHA_HASH);
        assertThat(entity.getDataCadastro()).isEqualTo(INSTANTE);
        assertThat(entity.getAtivo()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void idaEVoltaDevePreservarTodosOsCampos(boolean ativo) {
        Usuario original = Usuario.reconstituir(7L, NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, ativo);

        Usuario reconstituido = UsuarioPersistenceMapper.toDomain(UsuarioPersistenceMapper.toJpaEntity(original));

        assertThat(reconstituido.getId()).isEqualTo(7L);
        assertThat(reconstituido.getNome()).isEqualTo(NOME);
        assertThat(reconstituido.getCpf()).isEqualTo(CPF);
        assertThat(reconstituido.getEmail()).isEqualTo(EMAIL);
        assertThat(reconstituido.getSenhaHash()).isEqualTo(SENHA_HASH);
        assertThat(reconstituido.getDataCadastro()).isEqualTo(INSTANTE);
        assertThat(reconstituido.getAtivo()).isEqualTo(ativo);
    }

    @Test
    void leituraDeCpfInvalidoDeveViolarAInvarianteDoDominio() {
        UsuarioJpaEntity entity = new UsuarioJpaEntity(1L, NOME, "52998224724", EMAIL, SENHA_HASH, INSTANTE, true);

        assertThatThrownBy(() -> UsuarioPersistenceMapper.toDomain(entity))
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("USUARIO_CPF_INVALIDO");
    }

    @Test
    void leituraDeEmailInvalidoDeveViolarAInvarianteDoDominio() {
        UsuarioJpaEntity entity = new UsuarioJpaEntity(1L, NOME, CPF, "sem-arroba", SENHA_HASH, INSTANTE, true);

        assertThatThrownBy(() -> UsuarioPersistenceMapper.toDomain(entity))
                .isInstanceOf(DominioException.class)
                .extracting(e -> ((DominioException) e).getCodigo())
                .isEqualTo("USUARIO_EMAIL_INVALIDO");
    }

    @Test
    void leituraSemIdDeveSerRejeitadaComoDadoCorrompido() {
        UsuarioJpaEntity entity = new UsuarioJpaEntity(null, NOME, CPF, EMAIL, SENHA_HASH, INSTANTE, true);

        assertThatThrownBy(() -> UsuarioPersistenceMapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
