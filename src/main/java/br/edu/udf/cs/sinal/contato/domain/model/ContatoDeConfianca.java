package br.edu.udf.cs.sinal.contato.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;

import java.util.regex.Pattern;

// Entidade interna de ContatosDeConfianca. Imutável: a edição gera uma nova instância com o mesmo id.
public final class ContatoDeConfianca {

    public static final String NOME_OBRIGATORIO = "CONTATO_NOME_OBRIGATORIO";
    public static final String TELEFONE_OBRIGATORIO = "CONTATO_TELEFONE_OBRIGATORIO";
    public static final String TELEFONE_INVALIDO = "CONTATO_TELEFONE_INVALIDO";

    // Dígitos e formatação usual; o "+" só é aceito no início.
    private static final Pattern FORMATO_TELEFONE = Pattern.compile("\\+?[0-9 ().-]+");
    private static final String CODIGO_PAIS = "55";

    private final Long id;
    private final String nome;
    private final String telefone;

    private ContatoDeConfianca(Long id, String nome, String telefone) {
        this.id = id;
        this.nome = normalizarNome(nome);
        this.telefone = normalizarTelefone(telefone);
    }

    // Somente o agregado cria contatos novos; o id é atribuído pela persistência.
    static ContatoDeConfianca novo(String nome, String telefone) {
        return new ContatoDeConfianca(null, nome, telefone);
    }

    // Usada pela persistência; aplica as mesmas invariantes da criação.
    public static ContatoDeConfianca reconstituir(Long id, String nome, String telefone) {
        if (id == null) {
            throw new IllegalArgumentException("O id é obrigatório na reconstituição do contato.");
        }
        return new ContatoDeConfianca(id, nome, telefone);
    }

    ContatoDeConfianca comDados(String nome, String telefone) {
        return new ContatoDeConfianca(id, nome, telefone);
    }

    // CT2
    private static String normalizarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DominioException(NOME_OBRIGATORIO, "O nome do contato é obrigatório.");
        }
        return nome.trim();
    }

    // CT2 e CT3: telefone brasileiro com DDD, guardado somente com dígitos (DDD + número).
    private static String normalizarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            throw new DominioException(TELEFONE_OBRIGATORIO, "O telefone do contato é obrigatório.");
        }
        String aparado = telefone.trim();
        if (!FORMATO_TELEFONE.matcher(aparado).matches()) {
            throw telefoneInvalido();
        }
        String digitos = aparado.replaceAll("[^0-9]", "");
        // O tamanho distingue o código do país (12 ou 13 dígitos) do DDD 55 (10 ou 11 dígitos).
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith(CODIGO_PAIS)) {
            digitos = digitos.substring(CODIGO_PAIS.length());
        } else if (aparado.startsWith("+")) {
            throw telefoneInvalido();
        }
        if (!telefoneNacionalValido(digitos)) {
            throw telefoneInvalido();
        }
        return digitos;
    }

    // DDD com dois dígitos de 1 a 9; celular com 11 dígitos começando em 9; fixo com 10 dígitos começando em 2 a 5.
    private static boolean telefoneNacionalValido(String digitos) {
        if (digitos.length() != 10 && digitos.length() != 11) {
            return false;
        }
        if (digitos.charAt(0) == '0' || digitos.charAt(1) == '0') {
            return false;
        }
        char inicioDoNumero = digitos.charAt(2);
        if (digitos.length() == 11) {
            return inicioDoNumero == '9';
        }
        return inicioDoNumero >= '2' && inicioDoNumero <= '5';
    }

    private static DominioException telefoneInvalido() {
        return new DominioException(TELEFONE_INVALIDO, "Informe um telefone brasileiro válido com DDD.");
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getTelefone() {
        return telefone;
    }
}
