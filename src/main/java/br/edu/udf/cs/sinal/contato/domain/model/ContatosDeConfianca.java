package br.edu.udf.cs.sinal.contato.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Aggregate Root com identidade = usuarioId: um conjunto de contatos por usuário.
public class ContatosDeConfianca {

    public static final String LIMITE_CONTATOS_EXCEDIDO = "LIMITE_CONTATOS_EXCEDIDO";
    public static final String CONTATO_NAO_ENCONTRADO = "CONTATO_NAO_ENCONTRADO";

    public static final int LIMITE_CONTATOS = 5;

    private final Long usuarioId;
    private final List<ContatoDeConfianca> contatos;

    private ContatosDeConfianca(Long usuarioId, List<ContatoDeConfianca> contatos) {
        this.usuarioId = usuarioId;
        this.contatos = new ArrayList<>(contatos);
    }

    public static ContatosDeConfianca novo(Long usuarioId) {
        validarUsuarioId(usuarioId);
        return new ContatosDeConfianca(usuarioId, List.of());
    }

    // Usada pela persistência; falhas estruturais indicam dados corrompidos, e o limite segue CT1.
    public static ContatosDeConfianca reconstituir(Long usuarioId, List<ContatoDeConfianca> contatos) {
        validarUsuarioId(usuarioId);
        if (contatos == null) {
            throw new IllegalArgumentException("A lista de contatos é obrigatória na reconstituição.");
        }
        Set<Long> ids = new HashSet<>();
        for (ContatoDeConfianca contato : contatos) {
            if (contato == null || contato.getId() == null) {
                throw new IllegalArgumentException("Contatos reconstituídos devem existir e ter id.");
            }
            if (!ids.add(contato.getId())) {
                throw new IllegalArgumentException("Ids de contato repetidos na reconstituição.");
            }
        }
        if (contatos.size() > LIMITE_CONTATOS) {
            throw limiteExcedido();
        }
        return new ContatosDeConfianca(usuarioId, contatos);
    }

    // CT1, CT2 e CT3. Devolve o contato criado, ainda sem id.
    public ContatoDeConfianca adicionar(String nome, String telefone) {
        if (contatos.size() >= LIMITE_CONTATOS) {
            throw limiteExcedido();
        }
        ContatoDeConfianca contato = ContatoDeConfianca.novo(nome, telefone);
        contatos.add(contato);
        return contato;
    }

    // CT4: substitui nome e telefone, preservando id e posição.
    public void editar(Long contatoId, String nome, String telefone) {
        int indice = indiceExistente(contatoId);
        contatos.set(indice, contatos.get(indice).comDados(nome, telefone));
    }

    // CT4
    public void remover(Long contatoId) {
        contatos.remove(indiceExistente(contatoId));
    }

    public boolean possuiContato(Long contatoId) {
        return indiceDe(contatoId) >= 0;
    }

    private int indiceExistente(Long contatoId) {
        int indice = indiceDe(contatoId);
        if (indice < 0) {
            throw new DominioException(CONTATO_NAO_ENCONTRADO, "Contato não encontrado.");
        }
        return indice;
    }

    // Contatos ainda não persistidos (id nulo) nunca são localizados por id.
    private int indiceDe(Long contatoId) {
        if (contatoId == null) {
            return -1;
        }
        for (int i = 0; i < contatos.size(); i++) {
            if (contatoId.equals(contatos.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private static void validarUsuarioId(Long usuarioId) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("O usuarioId é obrigatório.");
        }
    }

    private static DominioException limiteExcedido() {
        return new DominioException(LIMITE_CONTATOS_EXCEDIDO,
                "O limite de " + LIMITE_CONTATOS + " contatos de confiança foi atingido.");
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public List<ContatoDeConfianca> getContatos() {
        return List.copyOf(contatos);
    }
}
