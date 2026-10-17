package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.repository.ContatosRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
public class AdicionarContatoService {

    private final ContatosRepository contatosRepository;

    public AdicionarContatoService(ContatosRepository contatosRepository) {
        this.contatosRepository = contatosRepository;
    }

    // O bloqueio vem antes da leitura: adições concorrentes do mesmo usuário são serializadas (CT1).
    @Transactional
    public ContatoDeConfiancaResumo executar(Long usuarioId, String nome, String telefone) {
        ContatosDeConfianca contatos = contatosRepository.buscarPorUsuarioParaAlteracao(usuarioId);
        contatos.adicionar(nome, telefone);
        ContatosDeConfianca salvo = contatosRepository.salvar(contatos);
        // O contato recém-criado recebe o maior id do usuário (Etapa 7.3).
        ContatoDeConfianca criado = salvo.getContatos().stream()
                .max(Comparator.comparing(ContatoDeConfianca::getId))
                .orElseThrow();
        return ContatoDeConfiancaResumo.de(criado);
    }
}
