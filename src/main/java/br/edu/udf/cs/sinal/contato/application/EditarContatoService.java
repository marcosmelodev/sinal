package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditarContatoService {

    private final ContatosRepository contatosRepository;

    public EditarContatoService(ContatosRepository contatosRepository) {
        this.contatosRepository = contatosRepository;
    }

    // CT4: contato inexistente ou de outro usuário gera CONTATO_NAO_ENCONTRADO (404, sem revelar existência).
    @Transactional
    public ContatoDeConfiancaResumo executar(Long usuarioId, Long contatoId, String nome, String telefone) {
        ContatosDeConfianca contatos = contatosRepository.buscarPorUsuarioParaAlteracao(usuarioId);
        contatos.editar(contatoId, nome, telefone);
        ContatosDeConfianca salvo = contatosRepository.salvar(contatos);
        return salvo.getContatos().stream()
                .filter(contato -> contatoId.equals(contato.getId()))
                .findFirst()
                .map(ContatoDeConfiancaResumo::de)
                .orElseThrow();
    }
}
