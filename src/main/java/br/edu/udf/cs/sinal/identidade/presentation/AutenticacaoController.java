package br.edu.udf.cs.sinal.identidade.presentation;

import br.edu.udf.cs.sinal.identidade.application.AutenticarUsuarioService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Rota pública (Etapa 4 §16 e §20). Refresh e logout: Etapa 10b.
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

    private final AutenticarUsuarioService autenticarUsuarioService;

    public AutenticacaoController(AutenticarUsuarioService autenticarUsuarioService) {
        this.autenticarUsuarioService = autenticarUsuarioService;
    }

    // O token não deve ser guardado em caches intermediários.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse resposta = LoginResponse.de(
                autenticarUsuarioService.executar(request.identificador(), request.senha()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resposta);
    }
}
