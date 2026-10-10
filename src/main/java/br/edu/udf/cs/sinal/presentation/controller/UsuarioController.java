package br.edu.udf.cs.sinal.presentation.controller;

import br.edu.udf.cs.sinal.application.command.CadastrarUsuarioCommand;
import br.edu.udf.cs.sinal.application.usecase.CadastrarUsuarioUseCase;
import br.edu.udf.cs.sinal.domain.model.Usuario;
import br.edu.udf.cs.sinal.presentation.dto.request.CadastrarUsuarioRequest;
import br.edu.udf.cs.sinal.presentation.dto.response.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final CadastrarUsuarioUseCase cadastrarUsuarioUseCase;

    public UsuarioController(CadastrarUsuarioUseCase cadastrarUsuarioUseCase) {
        this.cadastrarUsuarioUseCase = cadastrarUsuarioUseCase;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> cadastrar(@RequestBody @Valid CadastrarUsuarioRequest request){
        CadastrarUsuarioCommand command = new CadastrarUsuarioCommand(
                request.nome(),
                request.cpf(),
                request.email(),
                request.senha()
        );

        Usuario usuarioSalvo = cadastrarUsuarioUseCase.executar(command);

        return ResponseEntity.created(URI.create("api/v1/usuarios/" + usuarioSalvo.getId()))
                .body(UsuarioResponse.fromDomain(usuarioSalvo));
    }
}
