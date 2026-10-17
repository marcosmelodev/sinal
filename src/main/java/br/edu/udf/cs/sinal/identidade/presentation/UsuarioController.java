package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.identidade.application.CadastrarUsuarioService;
import br.edu.udf.cs.sinal.identidade.application.UsuarioCadastrado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final CadastrarUsuarioService cadastrarUsuarioService;

    public UsuarioController(CadastrarUsuarioService cadastrarUsuarioService) {
        this.cadastrarUsuarioService = cadastrarUsuarioService;
    }

    // Cadastro público (Etapa 4 §16). Erros são tratados por plataforma.web.GlobalExceptionHandler.
    @PostMapping
    public ResponseEntity<CadastrarUsuarioResponse> cadastrar(@RequestBody @Valid CadastrarUsuarioRequest request) {
        UsuarioCadastrado cadastrado = cadastrarUsuarioService.executar(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(cadastrado.id())
                .toUri();
        return ResponseEntity.created(location).body(CadastrarUsuarioResponse.de(cadastrado));
    }
}
