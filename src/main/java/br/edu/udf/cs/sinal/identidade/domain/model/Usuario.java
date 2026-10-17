package br.edu.udf.cs.sinal.identidade.domain.model;

import br.edu.udf.cs.sinal.compartilhado.domain.DominioException;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class Usuario {

    public static final String NOME_OBRIGATORIO = "USUARIO_NOME_OBRIGATORIO";
    public static final String CPF_INVALIDO = "USUARIO_CPF_INVALIDO";
    public static final String EMAIL_INVALIDO = "USUARIO_EMAIL_INVALIDO";
    public static final String SENHA_OBRIGATORIA = "USUARIO_SENHA_OBRIGATORIA";
    public static final String DATA_CADASTRO_OBRIGATORIA = "USUARIO_DATA_CADASTRO_OBRIGATORIA";

    private static final Pattern CPF_SOMENTE_DIGITOS = Pattern.compile("\\d{11}");
    private static final Pattern CPF_COM_MASCARA = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

    private static final int LIMITE_EMAIL = 254;
    private static final int LIMITE_PARTE_LOCAL_EMAIL = 64;
    // Parte local sem ponto nas pontas nem "..", domínio com rótulos sem hífen nas pontas e TLD alfabético.
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9%+_-]+(\\.[a-z0-9%+_-]+)*@([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}");

    private final Long id;
    private final String nome;
    private final String cpf;
    private final String email;
    private final String senhaHash;
    private final Instant dataCadastro;
    private final Boolean ativo;

    private Usuario(Long id, String nome, String cpf, String email, String senhaHash,
                    Instant dataCadastro, Boolean ativo) {
        this.id = id;
        this.nome = normalizarNome(nome);
        this.cpf = normalizarCpf(cpf);
        this.email = normalizarEmail(email);
        this.senhaHash = validarSenhaHash(senhaHash);
        this.dataCadastro = validarDataCadastro(dataCadastro);
        this.ativo = ativo;
    }

    public static Usuario cadastrar(String nome, String cpf, String email, String senhaHash, Instant instante) {
        return new Usuario(null, nome, cpf, email, senhaHash, instante, true);
    }

    // Usada pela persistência; aplica as mesmas invariantes do cadastro.
    public static Usuario reconstituir(Long id, String nome, String cpf, String email, String senhaHash,
                                       Instant dataCadastro, Boolean ativo) {
        if (id == null) {
            throw new IllegalArgumentException("O id é obrigatório na reconstituição do usuário.");
        }
        if (ativo == null) {
            throw new IllegalArgumentException("O indicador de ativo é obrigatório na reconstituição do usuário.");
        }
        return new Usuario(id, nome, cpf, email, senhaHash, dataCadastro, ativo);
    }

    // Formas canônicas para buscas (login por CPF ou e-mail, Etapa 4 §20), com as mesmas regras do cadastro.
    // Vazio quando o valor não é um CPF ou e-mail válido.
    public static Optional<String> cpfCanonico(String cpf) {
        try {
            return Optional.of(normalizarCpf(cpf));
        } catch (DominioException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> emailCanonico(String email) {
        try {
            return Optional.of(normalizarEmail(email));
        } catch (DominioException e) {
            return Optional.empty();
        }
    }

    private static String normalizarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DominioException(NOME_OBRIGATORIO, "O nome é obrigatório.");
        }
        return nome.trim();
    }

    // Aceita apenas 11 dígitos ou a máscara 000.000.000-00; guarda somente os dígitos.
    private static String normalizarCpf(String cpf) {
        if (cpf == null) {
            throw cpfInvalido();
        }
        String aparado = cpf.trim();
        String digitos;
        if (CPF_SOMENTE_DIGITOS.matcher(aparado).matches()) {
            digitos = aparado;
        } else if (CPF_COM_MASCARA.matcher(aparado).matches()) {
            digitos = aparado.replace(".", "").replace("-", "");
        } else {
            throw cpfInvalido();
        }
        // Sequências repetidas passam no cálculo dos dígitos verificadores, mas não são CPFs válidos.
        if (digitos.chars().distinct().count() == 1 || !digitosVerificadoresValidos(digitos)) {
            throw cpfInvalido();
        }
        return digitos;
    }

    private static boolean digitosVerificadoresValidos(String cpf) {
        return cpf.charAt(9) - '0' == calcularDigitoVerificador(cpf, 9)
                && cpf.charAt(10) - '0' == calcularDigitoVerificador(cpf, 10);
    }

    // Módulo 11: pesos decrescentes a partir de (quantidade + 1); resto menor que 2 resulta em 0.
    private static int calcularDigitoVerificador(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static DominioException cpfInvalido() {
        return new DominioException(CPF_INVALIDO, "O CPF informado é inválido.");
    }

    private static String normalizarEmail(String email) {
        if (email == null) {
            throw emailInvalido();
        }
        String normalizado = email.trim().toLowerCase(Locale.ROOT);
        int arroba = normalizado.indexOf('@');
        if (normalizado.length() > LIMITE_EMAIL
                || arroba > LIMITE_PARTE_LOCAL_EMAIL
                || !EMAIL.matcher(normalizado).matches()) {
            throw emailInvalido();
        }
        return normalizado;
    }

    private static DominioException emailInvalido() {
        return new DominioException(EMAIL_INVALIDO, "O e-mail informado é inválido.");
    }

    // O hash é preservado exatamente como recebido.
    private static String validarSenhaHash(String senhaHash) {
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new DominioException(SENHA_OBRIGATORIA, "A senha é obrigatória.");
        }
        return senhaHash;
    }

    private static Instant validarDataCadastro(Instant dataCadastro) {
        if (dataCadastro == null) {
            throw new DominioException(DATA_CADASTRO_OBRIGATORIA, "A data de cadastro é obrigatória.");
        }
        return dataCadastro;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getDataCadastro() {
        return dataCadastro;
    }

    public Boolean getAtivo() {
        return ativo;
    }
}
