package br.edu.udf.cs.sinal.identidade.application;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

// Login por um único identificador: com "@" é e-mail; caso contrário, CPF (Etapa 4 §20).
// Resposta genérica CREDENCIAIS_INVALIDAS sem revelar se o identificador existe, com tempo equalizado:
// o BCrypt roda em todos os caminhos, inclusive quando o usuário não existe. CONTA_INATIVA só com a senha correta.
@Service
public class AutenticarUsuarioService {

    public static final String CREDENCIAIS_INVALIDAS = "CREDENCIAIS_INVALIDAS";
    public static final String CONTA_INATIVA = "CONTA_INATIVA";

    // Limite do BCrypt: senhas maiores nunca foram aceitas no cadastro e o encoder as rejeitaria.
    private static final int SENHA_MAXIMO_BYTES = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmissorDeToken emissorDeToken;
    private final String hashFicticio;

    public AutenticarUsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                    EmissorDeToken emissorDeToken) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emissorDeToken = emissorDeToken;
        // Gerado com o mesmo encoder (mesmo custo) para igualar o tempo quando não há usuário.
        this.hashFicticio = passwordEncoder.encode("hash-ficticio-para-igualar-o-tempo-de-resposta");
    }

    @Transactional(readOnly = true)
    public TokenEmitido executar(String identificador, String senha) {
        Optional<Usuario> usuario = buscar(identificador);
        boolean senhaConfere = conferir(senha, usuario.map(Usuario::getSenhaHash).orElse(hashFicticio))
                && usuario.isPresent();
        if (!senhaConfere) {
            throw new AplicacaoException(CREDENCIAIS_INVALIDAS, "Dados incorretos.", Set.of());
        }
        if (!usuario.get().getAtivo()) {
            throw new AplicacaoException(CONTA_INATIVA, "Conta inativa.", Set.of());
        }
        return emissorDeToken.emitir(usuario.get().getId());
    }

    private Optional<Usuario> buscar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        if (identificador.contains("@")) {
            return Usuario.emailCanonico(identificador).flatMap(usuarioRepository::buscarPorEmail);
        }
        return Usuario.cpfCanonico(identificador).flatMap(usuarioRepository::buscarPorCpf);
    }

    // Sempre executa uma comparação BCrypt, mesmo para senha ausente ou longa demais.
    private boolean conferir(String senha, String hash) {
        boolean senhaAceitavel = senha != null && !senha.isEmpty()
                && senha.getBytes(StandardCharsets.UTF_8).length <= SENHA_MAXIMO_BYTES;
        boolean confere = passwordEncoder.matches(senhaAceitavel ? senha : "", hash);
        return senhaAceitavel && confere;
    }
}
