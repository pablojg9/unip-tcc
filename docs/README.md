# Documentação técnica do sistema antifraude

Esta pasta descreve o comportamento que está implementado no projeto. Ela foi organizada para atender tanto quem precisa entender o produto quanto quem precisa manter o código.

## Guia de leitura

1. [Arquitetura](ARCHITECTURE.md): componentes, responsabilidades, integrações, decisões arquiteturais e estado atual da execução.
2. [Banco de dados](DATABASE.md): schemas, tabelas, relacionamentos, índices e motivo de existência de cada estrutura.
3. [Modelos de fraude](MODELS.md): preparação dos dados, algoritmos, fórmulas, métricas e explicação de cada resultado.
4. [API HTTP](API.md): endpoints, parâmetros, respostas, filtros, autenticação e exemplos de uso.
5. [Fluxo completo](PROJECT_FLOW.md): caminho de um arquivo desde o upload até o dashboard, revisão humana e treinamento de um novo modelo.

## Visão rápida

O sistema recebe arquivos CSV ou Excel com sinistros, mantém o dado original para auditoria, normaliza as linhas válidas, publica cada sinistro no Kafka, calcula o risco no serviço Python e grava o resultado analítico no PostgreSQL. O dashboard Angular consulta esses resultados e permite a revisão humana.

```text
CSV/XLS/XLSX
    |
    v
Angular -> API Java -> Bronze -> Silver + Outbox -> Kafka
                                                   |
                                                   v
                                            Serviço Python/ML
                                                   |
                                                   v
Dashboard <- API Java <- Gold <- Kafka fraud-results
    |
    v
Revisão humana -> histórico de revisão + atualização da confirmação
```

## Glossário essencial

| Termo | Significado no projeto |
|---|---|
| Sinistro | Registro importado e analisado pelo sistema. |
| `transactionId` | Chave técnica interna e determinística do sinistro. |
| Referência pública | Código visual `SIN-AAAA-NNNNNNNN` calculado no frontend para não exibir o UUID. |
| `confirmedFraud` / `realFraud` | Rótulo conhecido no arquivo ou decisão posterior da revisão humana. Pode ser `null`. |
| `predictedFraud` | Decisão binária do modelo supervisionado. No modelo de anomalia permanece `null`. |
| `probability` / `riskScore` | Score entre 0 e 1. Sua interpretação depende de `scoreType`. |
| Bronze | Dado bruto e imutável, usado para rastreabilidade. |
| Silver | Dado limpo, normalizado e pronto para integração. |
| Gold | Resultado enriquecido da análise de fraude. |
| Outbox | Registro transacional do evento que ainda precisa ser publicado no Kafka. |

## Limites que precisam ser conhecidos

- O código `SIN-AAAA-NNNNNNN` é uma representação calculada no navegador. Ele é estável para o mesmo `transactionId`, mas não é persistido nem possui garantia matemática de unicidade absoluta.
- A codificação `case-...` usada na URL é Base64 URL-safe, não criptografia. Ela evita expor o UUID de forma direta na interface, mas pode ser revertida.
- Apenas os endpoints `POST /models/**` exigem autenticação no estado atual. Os demais endpoints estão públicos na configuração de segurança.
- O modelo de anomalia aponta casos incomuns; ele não declara que um caso é fraude.
- As explicações locais mostram sensibilidade do score ao retirar uma variável. Elas ajudam na investigação, mas não provam causalidade.

## Fonte da verdade desta documentação

As descrições foram derivadas de:

- backend em `tcc/src/main/java` e migrações em `tcc/src/main/resources/db/migration`;
- serviço de ML em `ml-fraud-py/fraud_detection`;
- frontend em `fraud-dashboard/src/app`;
- configurações em `application.yml`, `pyproject.toml`, `package.json` e `docker-compose.yml`.

