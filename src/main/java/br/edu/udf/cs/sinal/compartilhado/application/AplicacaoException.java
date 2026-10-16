package br.edu.udf.cs.sinal.compartilhado.application;

import java.util.Set;

// Regra da camada de aplicação (unicidade, política de senha); campos indica os dados de entrada envolvidos.
public class AplicacaoException extends RuntimeException {

    private final String codigo;
    private final String mensagem;
    private final Set<String> campos;

    public AplicacaoException(String codigo, String mensagem, Set<String> campos) {
        super(mensagem);
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("O código da exceção de aplicação é obrigatório.");
        }
        this.codigo = codigo;
        this.mensagem = mensagem;
        this.campos = campos == null ? Set.of() : Set.copyOf(campos);
    }

    public String getCodigo() {
        return codigo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Set<String> getCampos() {
        return campos;
    }
}
