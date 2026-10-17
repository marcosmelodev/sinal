package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoverContatoService {

    private final ContatosRepository contatosRepository;

    public RemoverContatoService(ContatosRepository contatosRepository) {
        this.contatosRepository = contatosRepository;
    }

    // CT4: contato inexistente ou de outro usuário gera CONTATO_NAO_ENCONTRADO (404, sem revelar existência).
    @Transactional
    public void executar(Long usuarioId, Long contatoId) {
        ContatosDeConfianca contatos = contatosRepository.buscarPorUsuarioParaAlteracao(usuarioId);
        contatos.remover(contatoId);
        contatosRepository.salvar(contatos);
    }
}
