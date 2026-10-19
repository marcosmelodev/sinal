package br.edu.udf.cs.sinal.libras.presentation;

import br.edu.udf.cs.sinal.identidade.infrastructure.security.UsuarioAutenticado;
import br.edu.udf.cs.sinal.libras.infrastructure.openai.OpenAiLibrasService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Exige Bearer JWT (default-deny da SegurancaConfig). Erros: plataforma.web.GlobalExceptionHandler.
@RestController
@RequestMapping("/api/v1/libras")
public class LibrasChatController {

    private final OpenAiLibrasService openAiLibrasService;

    public LibrasChatController(OpenAiLibrasService openAiLibrasService) {
        this.openAiLibrasService = openAiLibrasService;
    }

    // @UsuarioAutenticado confere no banco que a conta existe e está ativa antes de gastar uma chamada à IA.
    @PostMapping("/chat")
    public LibrasChatResponse conversar(@UsuarioAutenticado Long usuarioId,
                                        @RequestBody @Valid LibrasChatRequest request) {
        return new LibrasChatResponse(openAiLibrasService.responder(request.mensagem()));
    }
}
