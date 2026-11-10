# API HTTP

## Convenções

Base local padrão:

```text
http://localhost:8080/process_fraud_automotive
```

- JSON usa nomes em `camelCase`.
- Datas usam ISO-8601 local, por exemplo `2026-10-15T14:30:00`.
- `page` começa em zero.
- tamanhos de página são limitados pelo serviço ao intervalo de 1 a 100.
- upload usa `multipart/form-data` e o campo deve se chamar `file`.
- arquivos aceitos: `.csv`, `.xls` e `.xlsx`.
- limite padrão: 50 MiB.

## Autenticação

Somente os endpoints de escrita de modelos exigem HTTP Basic:

```text
POST /models/train
POST /models/{trainingId}/activate
```

Credenciais locais padrão:

```text
usuário: admin
senha: admin-local
```

Elas devem ser substituídas por `MODEL_ADMIN_USER` e `MODEL_ADMIN_PASSWORD` fora do ambiente local. Os demais endpoints estão públicos na configuração atual.

## Paginação

Respostas paginadas seguem:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## Erros

Erros tratados seguem este formato:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Minimum probability must be between 0 and 1",
  "timestamp": "2026-10-15T14:30:00"
}
```

Principais status:

| Status | Situação |
|---:|---|
| `400` | Parâmetro, corpo ou regra de negócio inválida. |
| `401` | Credencial Basic ausente ou inválida em endpoint protegido. |
| `403` | Usuário sem a função necessária. |
| `404` | Importação, sinistro, resultado ou treinamento não encontrado. |
| `413` | Arquivo maior que o limite multipart. |

## Importações

### `POST /imports`

Recebe um dataset de sinistros. Exige que exista um modelo ativo.

```bash
curl -X POST \
  -F 'file=@sinistros.xlsx' \
  http://localhost:8080/process_fraud_automotive/imports
```

Resposta `202 Accepted`:

```json
{
  "importId": "d485819f-5362-42ec-a9ee-193aa77e4ac0",
  "fileName": "sinistros.xlsx",
  "status": "QUEUED",
  "totalRows": 0,
  "processedRows": 0,
  "rejectedRows": 0,
  "errorMessage": null,
  "createdAt": "2026-10-15T14:30:00",
  "startedAt": null,
  "finishedAt": null,
  "batchExecutionId": 17
}
```

Se já existir arquivo com o mesmo SHA-256 em `QUEUED`, `PROCESSING`, `COMPLETED` ou `COMPLETED_WITH_WARNINGS`, o arquivo recém-gravado é descartado e o job existente é retornado.

### `GET /imports`

Lista importações.

```bash
curl 'http://localhost:8080/process_fraud_automotive/imports?page=0&size=20'
```

### `GET /imports/{importId}`

Consulta estado e contadores de uma importação.

### `POST /imports/{importId}/retry`

Reinicia uma execução Spring Batch. Só aceita importações `FAILED` que possuam `batchExecutionId`.

```bash
curl -X POST \
  http://localhost:8080/process_fraud_automotive/imports/d485819f-5362-42ec-a9ee-193aa77e4ac0/retry
```

Resposta: `202 Accepted` com `ImportJobView`.

### `GET /imports/{importId}/schema`

Mostra as colunas descobertas.

```json
[
  {
    "name": "valor_sinistro",
    "originalName": "Valor Sinistro",
    "type": "NUMBER",
    "role": "FEATURE"
  },
  {
    "name": "fraud",
    "originalName": "Fraud",
    "type": "BOOLEAN",
    "role": "LABEL"
  }
]
```

### `GET /imports/{importId}/errors`

Lista linhas rejeitadas.

Parâmetros: `page`, padrão 0; `size`, padrão 20.

```json
{
  "content": [
    {
      "id": 8,
      "importId": "d485819f-5362-42ec-a9ee-193aa77e4ac0",
      "fileName": "sinistros.xlsx",
      "rowNumber": 31,
      "originalData": {"Fraud": "talvez"},
      "errorReason": "Invalid confirmed fraud label: talvez",
      "rejectedAt": "2026-10-15T14:31:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## Sinistros normalizados

### `GET /claims`

Lista registros Silver.

| Parâmetro | Uso |
|---|---|
| `importId` | Restringe à importação. |
| `search` | Busca textual no JSON normalizado. |
| `field` | Nome normalizado de uma coluna dinâmica. Aceita `[a-z0-9_]`, até 120 caracteres. |
| `value` | Valor procurado em `field`; obrigatório quando `field` é usado. |
| `page` | Página, padrão 0. |
| `size` | Tamanho, padrão 20. |

```bash
curl --get \
  --data-urlencode 'field=marca_veiculo' \
  --data-urlencode 'value=Honda' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=20' \
  http://localhost:8080/process_fraud_automotive/claims
```

Item de resposta:

```json
{
  "transactionId": "02175c68-a4cf-3d4b-998d-e502657f9a9f",
  "importId": "d485819f-5362-42ec-a9ee-193aa77e4ac0",
  "rowNumber": 2,
  "sourceFile": "sinistros.xlsx",
  "confirmedFraud": null,
  "data": {
    "marca_veiculo": "Honda",
    "valor_sinistro": 18750.0
  },
  "processedAt": "2026-10-15T14:31:00"
}
```

### `GET /claims/{transactionId}`

Retorna um `ClaimRecord` ou `404`.

## Revisão humana

### `POST /claims/{transactionId}/reviews`

Registra uma revisão e atualiza `confirmedFraud` em Silver e Gold na mesma transação de banco.

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{
    "decision": "FRAUD",
    "notes": "Documentação incompatível com a ocorrência.",
    "reviewer": "analista@empresa.com"
  }' \
  http://localhost:8080/process_fraud_automotive/claims/02175c68-a4cf-3d4b-998d-e502657f9a9f/reviews
```

Valores de `decision`:

| Valor | Efeito |
|---|---|
| `FRAUD` | Define confirmação como `true`. |
| `LEGITIMATE` | Define confirmação como `false`. |
| `INCONCLUSIVE` | Define confirmação como `null`, devolvendo o caso à situação pendente. |

`notes` aceita até 2.000 caracteres e `reviewer` até 120.

Resposta:

```json
{
  "id": 42,
  "transactionId": "02175c68-a4cf-3d4b-998d-e502657f9a9f",
  "decision": "FRAUD",
  "notes": "Documentação incompatível com a ocorrência.",
  "reviewer": "analista@empresa.com",
  "reviewedAt": "2026-10-15T15:00:00"
}
```

### `GET /claims/{transactionId}/reviews`

Retorna todo o histórico do sinistro, do mais recente para o mais antigo.

## Dashboard

### `GET /dashboard/summary`

```json
{
  "totalBronze": 1000,
  "totalSilver": 990,
  "totalGold": 985,
  "totalRejected": 10,
  "realFraudsFromDataset": 72,
  "predictedFraudsByAi": 81,
  "predictedLegitimateByAi": 904,
  "pendingPrediction": 0,
  "predictedFraudPercentage": 8.223350253807107,
  "lowRisk": 760,
  "mediumRisk": 144,
  "highRisk": 81,
  "pendingReview": 410
}
```

Os totais Bronze, Silver e Gold podem ser diferentes: Bronze inclui linhas recebidas; Silver só linhas aceitas; Gold só resultados já consumidos do Kafka.

### `GET /dashboard/decision-details?predictedFraud=true`

Retorna métricas exatas da categoria selecionada no gráfico.

```json
{
  "predictedFraud": true,
  "total": 81,
  "percentage": 8.223350253807107,
  "averageProbability": 0.8432,
  "lowRisk": 0,
  "mediumRisk": 5,
  "highRisk": 76,
  "predominantModel": "supervised-rf-20261015122510-ab12cd34",
  "topReasons": [
    {
      "reason": "Valor sinistro aumentou o risco estimado em aproximadamente 11,0 pontos percentuais.",
      "occurrences": 19
    }
  ]
}
```

Use `predictedFraud=false` para a categoria legítima. Casos de anomalia, cujo valor é `null`, não pertencem a nenhuma dessas duas categorias.

### `GET /dashboard/confusion-matrix`

```json
{
  "truePositive": 61,
  "falsePositive": 20,
  "trueNegative": 480,
  "falseNegative": 11,
  "evaluated": 572,
  "accuracy": 0.9458,
  "precision": 0.7531,
  "recall": 0.8472,
  "f1Score": 0.7974
}
```

Somente casos com previsão binária e confirmação conhecida são avaliados.

### `GET /dashboard/results`

Lista resultados Gold com filtros combináveis.

| Parâmetro | Valores e regra |
|---|---|
| `predictedFraud` | `true` ou `false`. |
| `realFraud` | `true` ou `false`. |
| `minProbability` | Entre 0 e 1. |
| `startDate` | Data/hora ISO inclusiva. |
| `endDate` | Data/hora ISO inclusiva e não anterior a `startDate`. |
| `riskLevel` | Comparado em maiúsculas, normalmente `LOW`, `MEDIUM`, `HIGH`. |
| `modelVersion` | Correspondência exata. |
| `reviewStatus` | `PENDING` ou `REVIEWED`. |
| `page`, `size` | Padrões 0 e 10. |

`PENDING` significa `realFraud = null`; `REVIEWED` significa `realFraud != null`. Pendentes são ordenados primeiro por score decrescente e depois por data. As demais consultas são ordenadas pela data mais recente.

```bash
curl --get \
  --data-urlencode 'riskLevel=HIGH' \
  --data-urlencode 'reviewStatus=PENDING' \
  --data-urlencode 'minProbability=0.7' \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=20' \
  http://localhost:8080/process_fraud_automotive/dashboard/results
```

Item de resultado:

```json
{
  "transactionId": "02175c68-a4cf-3d4b-998d-e502657f9a9f",
  "realFraud": null,
  "predictedFraud": true,
  "probability": 0.82,
  "scoreType": "FRAUD_PROBABILITY",
  "riskLevel": "HIGH",
  "threshold": 0.5,
  "classification": "Suspicious transaction",
  "modelVersion": "supervised-rf-20261015122510-ab12cd34",
  "reasons": ["Valor sinistro aumentou o risco estimado em aproximadamente 11,0 pontos percentuais."],
  "explanation": "O modelo classificou o sinistro como high, com score de 82,0%...",
  "explanationType": "DETERMINISTIC",
  "explanationModel": null,
  "processedAt": "2026-10-15T14:32:00"
}
```

### `GET /dashboard/results/{transactionId}`

Retorna os campos do resultado e acrescenta:

```json
{
  "sourceFile": "sinistros.xlsx",
  "ingestedAt": "2026-10-15T14:31:00",
  "features": {
    "marca_veiculo": "Honda",
    "valor_sinistro": 18750.0
  }
}
```

No contrato atual, `ingestedAt` recebe o `processedAt` da linha Silver. Não é necessariamente o `bronze.claim_raw.ingested_at`.

## Modelos

### `GET /models`

Retorna os 50 treinamentos mais recentes.

```json
[
  {
    "trainingId": "5f195c82-31a0-4ac6-871f-f86246df4cec",
    "fileName": "treinamento.csv",
    "datasetHash": "a4d2...",
    "status": "ACTIVE",
    "requestedBy": "admin",
    "requestedAt": "2026-10-15T12:20:00",
    "completedAt": "2026-10-15T12:25:10",
    "modelVersion": "supervised-rf-20261015122510-ab12cd34",
    "modelType": "SUPERVISED",
    "rowCount": 10000,
    "featureCount": 18,
    "targetColumn": "fraud",
    "metrics": {"accuracy": 0.94, "recall": 0.81},
    "errorMessage": null,
    "active": true,
    "activatedAt": "2026-10-15T12:27:00",
    "activatedBy": "admin"
  }
]
```

### `POST /models/train`

Cria e envia um treinamento assíncrono.

```bash
curl -X POST \
  -u 'admin:admin-local' \
  -F 'file=@treinamento.csv' \
  http://localhost:8080/process_fraud_automotive/models/train
```

Resposta `202 Accepted`, inicialmente em `TRAINING`. O resultado posterior será `READY` ou `FAILED`.

### `POST /models/{trainingId}/activate`

Solicita ativação de um candidato `READY` ou `ACTIVATION_FAILED`.

```bash
curl -X POST \
  -u 'admin:admin-local' \
  http://localhost:8080/process_fraud_automotive/models/5f195c82-31a0-4ac6-871f-f86246df4cec/activate
```

Resposta `202 Accepted`, inicialmente em `ACTIVATING`. O retorno assíncrono leva o modelo a `ACTIVE` ou `ACTIVATION_FAILED`.

## Observações de contrato

- Os exemplos representam o formato do código, não dados reais do ambiente.
- O `transactionId` ainda é necessário nas APIs. A referência `SIN-...` existe apenas no frontend atual.
- O resultado da inferência é assíncrono. Uma linha pode estar em Silver antes de existir em Gold.
- Não há endpoint HTTP direto de scoring; a inferência de produção passa pelo Kafka.
- Não há especificação OpenAPI/Swagger configurada no projeto atual. Este arquivo é o contrato humano de referência.

