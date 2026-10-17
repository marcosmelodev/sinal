package br.edu.udf.cs.sinal.chamado.presentation;

import br.edu.udf.cs.sinal.chamado.application.ChamadoRegistradoResultado;
import br.edu.udf.cs.sinal.chamado.application.RegistrarChamadoService;
import br.edu.udf.cs.sinal.chamado.application.query.ListarChamadosHistoricoQuery;
import br.edu.udf.cs.sinal.identidade.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

// Rotas da Etapa 4 §16. 201 no primeiro registro e 200 no replay (Etapa 4 §17).
// O Idempotency-Key é validado no serviço para que a ausência também responda em problem+json.
@RestController
@RequestMapping("/api/v1/chamados")
public class ChamadoController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RegistrarChamadoService registrarChamadoService;
    private final ListarChamadosHistoricoQuery listarChamadosHistoricoQuery;

    public ChamadoController(RegistrarChamadoService registrarChamadoService,
                             ListarChamadosHistoricoQuery listarChamadosHistoricoQuery) {
        this.registrarChamadoService = registrarChamadoService;
        this.listarChamadosHistoricoQuery = listarChamadosHistoricoQuery;
    }

    // Histórico do usuário autenticado, do mais recente ao mais antigo, paginado por cursor (Etapa 4 §16).
    @GetMapping
    public PaginaDeHistoricoResponse listarHistorico(@UsuarioAutenticado Long usuarioId,
                                                     @RequestParam(required = false) Integer limite,
                                                     @RequestParam(required = false) String cursor) {
        return PaginaDeHistoricoResponse.de(listarChamadosHistoricoQuery.executar(usuarioId, limite, cursor));
    }

    @PostMapping
    public ResponseEntity<ChamadoResponse> registrar(@UsuarioAutenticado Long usuarioId,
                                                     @RequestHeader(value = IDEMPOTENCY_KEY, required = false)
                                                     String chaveIdempotencia,
                                                     @RequestBody @Valid RegistrarChamadoRequest request) {
        return responder(registrarChamadoService.registrarPadrao(usuarioId, chaveIdempotencia, request.orgao(),
                request.ocorrenciaId(), request.detalheAdicional(), request.localizacao().toInformada()));
    }

    @PostMapping("/emergencia-imediata")
    public ResponseEntity<ChamadoResponse> registrarEmergenciaImediata(
            @UsuarioAutenticado Long usuarioId,
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String chaveIdempotencia,
            @RequestBody @Valid RegistrarEmergenciaImediataRequest request) {
        return responder(registrarChamadoService.registrarEmergenciaImediata(usuarioId, chaveIdempotencia,
                request.localizacao().toInformada()));
    }

    private static ResponseEntity<ChamadoResponse> responder(ChamadoRegistradoResultado resultado) {
        ChamadoResponse corpo = ChamadoResponse.de(resultado);
        if (resultado.replay()) {
            return ResponseEntity.ok(corpo);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/chamados/{id}")
                .buildAndExpand(resultado.id())
                .toUri();
        return ResponseEntity.created(location).body(corpo);
    }
}
