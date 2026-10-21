# Testes de carga (k6) — chat em LIBRAS

Alvo: `POST /api/v1/libras/chat` (Bearer JWT). Esperado sob carga: `200 {"resposta": "..."}` ou
`503 {"code": "CHAT_INDISPONIVEL", ...}`. Nunca 500, nunca requisição pendurada.

## Preparação (uma vez)

```bash
brew install k6
docker compose up -d                       # MySQL
cp load-tests/.env.example load-tests/.env
# com a aplicação no ar, cadastre o usuário de teste:
curl -X POST http://localhost:8080/api/v1/usuarios -H 'Content-Type: application/json' \
  -d '{"nome":"Carga k6","cpf":"52998224725","email":"carga.k6@exemplo.com","senha":"SenhaDeCarga123"}'
```

## Cenário A — OpenAI real (custa créditos; sujeito ao rate limit da conta)

```bash
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
set -a && source load-tests/.env && set +a && k6 run load-tests/chat-stress-test.js
```

## Cenário B — gargalo simulado (mock local, sem custo)

```bash
MOCK_MODO=lento node load-tests/openai-mock.mjs            # ou ok | erro | misto
OPENAI_API_KEY=mock ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--openai.api.url=http://localhost:8089/v1/chat/completions --openai.api.timeout-resposta=PT3S"
set -a && source load-tests/.env && set +a && P95_MS=4000 k6 run load-tests/chat-stress-test.js
```

No modo `lento`, todas as respostas devem ser 503 `CHAT_INDISPONIVEL` em ~3 s, sem nenhum 500.
Para exportar o resultado: acrescente `--summary-export=load-tests/resultado.json` ao `k6 run`.
