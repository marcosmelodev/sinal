package br.edu.udf.cs.sinal.compartilhado.domain;

public class DominioException extends RuntimeException {

    private final String codigo;
    private final String mensagem;

    public DominioException(String codigo, String mensagem) {
        super(mensagem);
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("O código da exceção de domínio é obrigatório.");
        }
        this.codigo = codigo;
        this.mensagem = mensagem;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getMensagem() {
        return mensagem;
    }
}
