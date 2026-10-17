package br.edu.udf.cs.sinal.contato.application;

import br.edu.udf.cs.sinal.contato.domain.model.ContatoDeConfianca;
import br.edu.udf.cs.sinal.contato.domain.model.ContatosDeConfianca;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

// Apoio aos testes unitários dos serviços de contato.
final class ContatosDeTeste {

    static final Long USUARIO_ID = 1L;
    static final String TELEFONE = "61999998888";

    private ContatosDeTeste() {
    }

    // Agregado reconstituído com contatos de ids 1..quantidade.
    static ContatosDeConfianca comContatos(int quantidade) {
        return ContatosDeConfianca.reconstituir(USUARIO_ID, LongStream.rangeClosed(1, quantidade)
                .mapToObj(id -> ContatoDeConfianca.reconstituir(id, "Contato " + id, TELEFONE))
                .toList());
    }

    // Simula o adapter: atribui ids crescentes aos contatos novos (id nulo), como o AUTO_INCREMENT.
    static ContatosDeConfianca simularPersistencia(ContatosDeConfianca contatos) {
        long proximo = contatos.getContatos().stream().filter(c -> c.getId() != null)
                .mapToLong(ContatoDeConfianca::getId).max().orElse(0) + 100;
        List<ContatoDeConfianca> persistidos = new ArrayList<>();
        for (ContatoDeConfianca contato : contatos.getContatos()) {
            Long id = contato.getId() != null ? contato.getId() : proximo++;
            persistidos.add(ContatoDeConfianca.reconstituir(id, contato.getNome(), contato.getTelefone()));
        }
        return ContatosDeConfianca.reconstituir(contatos.getUsuarioId(), persistidos);
    }

    static Transactional transacaoDe(Class<?> servico) {
        Method executar = Arrays.stream(servico.getDeclaredMethods())
                .filter(m -> m.getName().equals("executar"))
                .findFirst().orElseThrow();
        return executar.getAnnotation(Transactional.class);
    }
}
