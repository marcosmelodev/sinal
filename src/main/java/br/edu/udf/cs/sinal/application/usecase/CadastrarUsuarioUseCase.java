package br.edu.udf.cs.sinal.application.usecase;

import br.edu.udf.cs.sinal.application.command.CadastrarUsuarioCommand;
import br.edu.udf.cs.sinal.domain.model.Usuario;
import br.edu.udf.cs.sinal.domain.repositories.UsuarioRepository;
import org.springframework.stereotype.Service;

@Service
public class CadastrarUsuarioUseCase {

    private final UsuarioRepository usuarioRepository;

    public CadastrarUsuarioUseCase(UsuarioRepository usuarioRepository){
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario executar(CadastrarUsuarioCommand command){
        String cpfNormalizado = Usuario.normalizarCpf(command.cpf());
        String emailNormalizado = command.email().trim().toLowerCase();

        if (usuarioRepository.buscarPorCpf(cpfNormalizado).isPresent()) {
            throw new IllegalArgumentException("CPF já cadastrado.");
        }
        if (usuarioRepository.buscarPorEmail(emailNormalizado).isPresent()) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }

        //será alterado ao implementar security
        Usuario novoUsuario = new Usuario(
                command.nome(),
                cpfNormalizado,
                emailNormalizado,
                command.senha()
        );
        return usuarioRepository.salvar(novoUsuario);
    }
}
