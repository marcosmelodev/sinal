package br.edu.udf.cs.sinal.chamado.application.query;

import java.util.List;

// Leitura por projeção (Etapa 4 §12): não hidrata o agregado Chamado.
public interface ChamadoConsulta {

    // Chamados do usuário do mais recente ao mais antigo (empate: id crescente), a partir da posição
    // informada (exclusive) ou do início quando nula.
    List<ChamadoHistoricoItem> listarHistorico(Long usuarioId, PosicaoNoHistorico depoisDe, int quantidade);
}
