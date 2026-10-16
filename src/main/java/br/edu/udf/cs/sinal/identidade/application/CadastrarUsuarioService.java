package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class CadastrarUsuarioService {

    public static final String CADASTRO_JA_EXISTENTE = "CADASTRO_JA_EXISTENTE";
    public static final String SENHA_INVALIDA = "SENHA_INVALIDA";

    static final int SENHA_MINIMO_CARACTERES = 8;
    // Limite do BCrypt: bytes além do 72º seriam ignorados, e o encoder os rejeita.
    static final int SENHA_MAXIMO_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public CadastrarUsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // O hash é calculado antes da checagem de duplicidade: o tempo de resposta não revela CPF ou e-mail existentes.
    @Transactional
    public UsuarioCadastrado executar(CadastrarUsuarioCommand command) {
        validarSenha(command.senha());
        String senhaHash = passwordEncoder.encode(command.senha());
        Usuario usuario = Usuario.cadastrar(command.nome(), command.cpf(), command.email(), senhaHash,
                Instant.now(clock));

        // As buscas usam os valores já normalizados pelo domínio.
        Set<String> emUso = new LinkedHashSet<>();
        if (usuarioRepository.buscarPorCpf(usuario.getCpf()).isPresent()) {
            emUso.add("cpf");
        }
        if (usuarioRepository.buscarPorEmail(usuario.getEmail()).isPresent()) {
            emUso.add("email");
        }
        if (!emUso.isEmpty()) {
            throw cadastroJaExistente(emUso);
        }

        try {
            return UsuarioCadastrado.de(usuarioRepository.salvar(usuario));
        } catch (DataIntegrityViolationException e) {
            // Corrida: outro cadastro passou pela checagem ao mesmo tempo e o UNIQUE do banco decidiu.
            throw traduzirViolacao(e);
        }
    }

    // Política de senha (Application): obrigatória, não aparada, 8 caracteres a 72 bytes em UTF-8.
    private static void validarSenha(String senha) {
        if (senha == null || senha.isBlank()
                || senha.codePointCount(0, senha.length()) < SENHA_MINIMO_CARACTERES
                || senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMO_BYTES) {
            throw new AplicacaoException(SENHA_INVALIDA,
                    "A senha deve ter no mínimo " + SENHA_MINIMO_CARACTERES + " caracteres e no máximo "
                            + SENHA_MAXIMO_BYTES + " bytes.", Set.of("senha"));
        }
    }

    // Identifica o campo pela restrição da V1; violação que não seja de chave duplicada não é traduzida.
    private static RuntimeException traduzirViolacao(DataIntegrityViolationException e) {
        String mensagem = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        Set<String> campos = new LinkedHashSet<>();
        if (mensagem.contains("uk_usuario_cpf")) {
            campos.add("cpf");
        }
        if (mensagem.contains("uk_usuario_email")) {
            campos.add("email");
        }
        if (campos.isEmpty() && !mensagem.contains("duplicate")) {
            return e;
        }
        return cadastroJaExistente(campos);
    }

    private static AplicacaoException cadastroJaExistente(Set<String> campos) {
        return new AplicacaoException(CADASTRO_JA_EXISTENTE, "Já existe cadastro com os dados informados.", campos);
    }
}
