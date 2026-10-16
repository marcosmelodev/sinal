package br.edu.udf.cs.sinal.chamado.domain.repository;

import br.edu.udf.cs.sinal.chamado.domain.model.Chamado;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// A chave de idempotência é parâmetro técnico das operações, não atributo do Chamado (Etapa 5 §15).
// Consultas de tela (Histórico, Detalhes, Acompanhamento) usam projeções, não este repositório.
public interface ChamadoRepository {

    // Insere um Chamado novo (id nulo) com sua transição inicial; devolve-o com o id gerado.
    // Chave repetida para o mesmo usuário viola UNIQUE(usuario_id, chave_idempotencia).
    Chamado salvarNovo(Chamado chamado, String chaveIdempotencia);

    // Persiste o estado atual e acrescenta as transições ainda não gravadas; o histórico nunca é alterado.
    Chamado salvar(Chamado chamado);

    Optional<Chamado> buscarPorId(Long id);

    // Exige transação ativa e bloqueia transições concorrentes do mesmo Chamado até o fim dela.
    Optional<Chamado> buscarPorIdParaAlteracao(Long id);

    Optional<Chamado> buscarPorUsuarioEChaveIdempotencia(Long usuarioId, String chaveIdempotencia);

    // Reconciliação do job: Chamados em CRIADO sem Encaminhamento, criados antes do corte, mais antigos primeiro.
    List<Chamado> buscarCriadosSemEncaminhamento(Instant criadosAntesDe, int limite);
}
