package br.edu.udf.cs.sinal.contato.presentation;

import br.edu.udf.cs.sinal.contato.application.AdicionarContatoService;
import br.edu.udf.cs.sinal.contato.application.ContatoDeConfiancaResumo;
import br.edu.udf.cs.sinal.contato.application.EditarContatoService;
import br.edu.udf.cs.sinal.contato.application.ListarContatosService;
import br.edu.udf.cs.sinal.contato.application.RemoverContatoService;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

// Rotas da Etapa 4 §16, sempre escopadas ao usuário autenticado. Erros: plataforma.web.GlobalExceptionHandler.
@RestController
@RequestMapping("/api/v1/contatos-confianca")
public class ContatoController {

    private final ListarContatosService listarContatosService;
    private final AdicionarContatoService adicionarContatoService;
    private final EditarContatoService editarContatoService;
    private final RemoverContatoService removerContatoService;

    public ContatoController(ListarContatosService listarContatosService,
                             AdicionarContatoService adicionarContatoService,
                             EditarContatoService editarContatoService,
                             RemoverContatoService removerContatoService) {
        this.listarContatosService = listarContatosService;
        this.adicionarContatoService = adicionarContatoService;
        this.editarContatoService = editarContatoService;
        this.removerContatoService = removerContatoService;
    }

    @GetMapping
    public List<ContatoResponse> listar(@UsuarioAutenticado Long usuarioId) {
        return listarContatosService.executar(usuarioId).stream().map(ContatoResponse::de).toList();
    }

    @PostMapping
    public ResponseEntity<ContatoResponse> adicionar(@UsuarioAutenticado Long usuarioId,
                                                     @RequestBody @Valid AdicionarContatoRequest request) {
        ContatoDeConfiancaResumo criado = adicionarContatoService.executar(usuarioId, request.nome(),
                request.telefone());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(criado.id())
                .toUri();
        return ResponseEntity.created(location).body(ContatoResponse.de(criado));
    }

    @PutMapping("/{id}")
    public ContatoResponse editar(@UsuarioAutenticado Long usuarioId, @PathVariable Long id,
                                  @RequestBody @Valid EditarContatoRequest request) {
        return ContatoResponse.de(editarContatoService.executar(usuarioId, id, request.nome(), request.telefone()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@UsuarioAutenticado Long usuarioId, @PathVariable Long id) {
        removerContatoService.executar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
