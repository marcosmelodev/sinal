package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListarContatosService {

    private final ContatosRepository contatosRepository;

    public ListarContatosService(ContatosRepository contatosRepository) {
        this.contatosRepository = contatosRepository;
    }

    // Somente leitura: sem bloqueio; a ordem é a do agregado.
    @Transactional(readOnly = true)
    public List<ContatoDeConfiancaResumo> executar(Long usuarioId) {
        return contatosRepository.buscarPorUsuario(usuarioId).getContatos().stream()
                .map(ContatoDeConfiancaResumo::de)
                .toList();
    }
}
