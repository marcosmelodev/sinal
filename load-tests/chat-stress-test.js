// Teste de carga do chat em LIBRAS: POST /api/v1/libras/chat (exige Bearer JWT).
//
// Objetivo: sob carga, o endpoint responde 200 com o texto da IA ou, se a OpenAI falhar ou estourar o tempo
// limite, 503 com code "CHAT_INDISPONIVEL" (contrato do GlobalExceptionHandler). Nunca 500, nunca pendurado.
//
// Execução: ver load-tests/README.md.

import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const EMAIL = __ENV.SINAL_EMAIL;
const SENHA = __ENV.SINAL_SENHA;

const VUS_PICO = Number(__ENV.VUS_PICO || 20);
const RAMP_UP = __ENV.RAMP_UP || '1m';
const PLATO = __ENV.PLATO || '3m';
const RAMP_DOWN = __ENV.RAMP_DOWN || '1m';
const P95_MS = Number(__ENV.P95_MS || 8000);
const THINK_TIME_MAX = Number(__ENV.THINK_TIME_MAX || 3);
// Acima do pior caso do backend (conexão 5s + resposta 20s): se o k6 estourar, a aplicação travou.
const TIMEOUT_REQUISICAO = __ENV.TIMEOUT_REQUISICAO || '40s';

const http500 = new Rate('chat_http_500');
const fallback = new Rate('chat_fallback_indisponivel');
const respostaIa = new Rate('chat_resposta_ia');

// 503 é a degradação esperada (fallback), não falha: http_req_failed passa a contar só o inesperado
// (500, 401, timeout do próprio k6 etc.).
http.setResponseCallback(http.expectedStatuses(200, 503));

const CHAT = { endpoint: 'chat' };

export const options = {
  scenarios: {
    estresse_chat: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: VUS_PICO },   // ramp-up
        { duration: PLATO, target: VUS_PICO },     // platô / estresse
        { duration: RAMP_DOWN, target: 0 },        // ramp-down
      ],
      // Deixa terminar requisições presas no tempo limite da OpenAI durante a descida.
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:chat}': [`p(95)<${P95_MS}`],
    chat_http_500: ['rate<0.01'],
    'http_req_failed{endpoint:chat}': ['rate<0.01'],
    'checks{endpoint:chat}': ['rate>0.99'],
  },
};

const MENSAGENS = [
  'Socorro, meu pai caiu e bateu a cabeça.',
  'Tem fumaça saindo da cozinha do vizinho.',
  'Uma pessoa desmaiou na rua, o que eu faço?',
  'Estou com dor forte no peito.',
  'Meu filho engoliu uma moeda.',
  'Sofri um acidente de carro, estou bem mas o outro motorista está ferido.',
  'Alguém está tentando entrar na minha casa.',
  'Minha mãe está com falta de ar.',
];

// O access token vale 15 min (sinal.seguranca.jwt.validade): mantenha a duração total abaixo disso.
export function setup() {
  if (!EMAIL || !SENHA) {
    fail('Defina SINAL_EMAIL e SINAL_SENHA (ver load-tests/.env.example).');
  }
  const res = http.post(`${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identificador: EMAIL, senha: SENHA }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } });
  if (res.status !== 200) {
    fail(`Login falhou (HTTP ${res.status}). Cadastre o usuário de teste antes (ver README).`);
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const mensagem = MENSAGENS[Math.floor(Math.random() * MENSAGENS.length)];
  const res = http.post(`${BASE_URL}/api/v1/libras/chat`, JSON.stringify({ mensagem }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
    tags: CHAT,
    timeout: TIMEOUT_REQUISICAO,
  });

  const corpo = lerJson(res);
  const ehRespostaIa = res.status === 200 && typeof corpo.resposta === 'string' && corpo.resposta.trim() !== '';
  const ehFallback = res.status === 503 && corpo.code === 'CHAT_INDISPONIVEL';

  http500.add(res.status === 500, CHAT);
  fallback.add(ehFallback, CHAT);
  respostaIa.add(ehRespostaIa, CHAT);

  check(res, {
    'status é 200 (IA) ou 503 (fallback)': (r) => r.status === 200 || r.status === 503,
    'corpo traz resposta da IA ou CHAT_INDISPONIVEL': () => ehRespostaIa || ehFallback,
  }, CHAT);

  // Tempo de leitura/digitação do usuário entre mensagens.
  sleep(1 + Math.random() * Math.max(0, THINK_TIME_MAX - 1));
}

function lerJson(res) {
  try {
    return res.json() || {};
  } catch (e) {
    return {};
  }
}
