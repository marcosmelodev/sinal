// Mock local da OpenAI (chat completions) para simular gargalo sem gastar a API real. Sem dependências.
//
//   MOCK_MODO=ok     responde em MOCK_ATRASO_MS (padrão 800 ms)
//   MOCK_MODO=lento  demora MOCK_ATRASO_MS (padrão 30000 ms), acima do timeout do backend -> CHAT_INDISPONIVEL
//   MOCK_MODO=erro   responde HTTP 500 -> CHAT_INDISPONIVEL
//   MOCK_MODO=misto  50% ok, 25% lento, 25% erro
//
// Uso: MOCK_MODO=lento node load-tests/openai-mock.mjs   (porta MOCK_PORTA, padrão 8089)

import http from 'node:http';

const PORTA = Number(process.env.MOCK_PORTA || 8089);
const MODO = process.env.MOCK_MODO || 'ok';
const ATRASO_OK = Number(process.env.MOCK_ATRASO_MS || 800);
const ATRASO_LENTO = Number(process.env.MOCK_ATRASO_MS || 30000);

function sortearModo() {
  if (MODO !== 'misto') return MODO;
  const n = Math.random();
  return n < 0.5 ? 'ok' : n < 0.75 ? 'lento' : 'erro';
}

http.createServer((req, res) => {
  req.resume();
  req.on('end', () => {
    const modo = sortearModo();
    if (modo === 'erro') {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'mock: erro simulado' } }));
      return;
    }
    const atraso = modo === 'lento' ? ATRASO_LENTO : ATRASO_OK;
    setTimeout(() => {
      if (res.destroyed) return; // o backend já desistiu (tempo limite)
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        choices: [{ message: { role: 'assistant', content: 'Fique calmo. Onde você está? Alguém está ferido?' } }],
      }));
    }, atraso);
  });
}).listen(PORTA, () => console.log(`Mock OpenAI em http://localhost:${PORTA} (modo ${MODO})`));
