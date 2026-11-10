# Modelos de fraude e cálculo dos resultados

## O que o sistema realmente calcula

O serviço escolhe automaticamente uma de duas estratégias de treinamento:

| Dataset | Estratégia | Resultado principal |
|---|---|---|
| Possui uma coluna de fraude reconhecida | Random Forest supervisionada | Probabilidade estimada de fraude e decisão binária. |
| Não possui coluna de fraude reconhecida | Isolation Forest não supervisionada | Grau normalizado de anomalia e indicação de revisão. |

Essa distinção é essencial. Uma anomalia não é uma fraude comprovada, e o campo `probability` de um modelo de anomalia não é uma probabilidade estatística de fraude.

## Descoberta da coluna alvo

Por padrão, os aliases aceitos são:

```text
fraudfound_p, fraud, is_fraud, fraude, isfraud, label
```

Os nomes são normalizados antes da comparação:

1. remoção de acentos;
2. conversão para minúsculas;
3. remoção de espaços laterais;
4. troca de sequências não alfanuméricas por `_`;
5. remoção de `_` no início e no fim.

Exemplos:

| Original | Normalizado |
|---|---|
| `Idade do Segurado` | `idade_do_segurado` |
| `Valor (R$)` | `valor_r` |
| `É Fraude?` | `e_fraude` |

Se dois nomes diferentes virarem o mesmo nome normalizado, o dataset é rejeitado. Se mais de uma coluna corresponder a um alias de fraude, o treinamento também é rejeitado.

## Conversão do rótulo

Valores reconhecidos como fraude:

```text
1, true, yes, sim, fraud, fraude
```

Valores reconhecidos como legítimo:

```text
0, false, no, nao, não, legitimate, legitimo, legítimo
```

Valores vazios permanecem sem rótulo e não entram no treino supervisionado. Qualquer valor não vazio fora do vocabulário válido gera erro, evitando ensinar uma classe incorreta silenciosamente.

## Regras de qualidade antes do treino

O pipeline aplica regras conservadoras:

- remove linhas exatamente duplicadas;
- troca idades menores ou iguais a 0 e maiores que 120 por valor ausente;
- troca marcadores vazios de colunas de calendário por valor ausente;
- remove identificadores que poderiam causar memorização, como `id`, `claim_id`, `transaction_id`, `id_sinistro`, `numero_apolice`, `segurado_id`, `policy_number` e `rep_number`;
- remove colunas inteiramente vazias;
- registra hash SHA-256 do dataset no artefato e no relatório.

Substituir um dado inválido por ausente permite que o imputador aplique uma referência aprendida. Isso é preferível a tratar, por exemplo, idade `0` como uma idade real.

## Inferência dos tipos de atributos

Cada coluna é classificada como numérica ou categórica:

- tipos numéricos nativos são numéricos;
- uma coluna textual é numérica quando pelo menos 95% dos valores não vazios podem ser convertidos para número;
- booleanos são tratados como categorias;
- as demais colunas são categóricas.

Valores infinitos em colunas numéricas viram ausentes.

### Pré-processamento numérico

- ausentes são preenchidos pela mediana da coluna;
- no Random Forest não há padronização;
- no Isolation Forest há `StandardScaler` depois da imputação.

### Pré-processamento categórico

- ausentes são preenchidos pelo valor mais frequente;
- categorias passam por One-Hot Encoding;
- uma categoria que aparece apenas na inferência é ignorada com segurança por `handle_unknown="ignore"`.

## Modelo supervisionado: Random Forest

### Condições mínimas

- pelo menos 8 linhas rotuladas;
- presença das duas classes;
- pelo menos 2 exemplos de cada classe.

### Separação de treino e teste

O dataset rotulado é dividido de forma estratificada:

- `test_size = 0.25` por padrão;
- `random_state = 42` por padrão;
- a estratificação preserva aproximadamente a proporção entre fraude e legítimo.

O holdout é usado para medir desempenho em dados não vistos. Depois da avaliação, o pipeline é treinado novamente com todas as linhas rotuladas para produzir o artefato final.

### Hiperparâmetros implementados

```text
RandomForestClassifier
  n_estimators = 300
  class_weight = balanced_subsample
  min_samples_leaf = 2
  random_state = 42
  n_jobs = -1
```

`balanced_subsample` reduz o impacto do desbalanceamento entre fraudes e casos legítimos. `min_samples_leaf = 2` evita folhas formadas por um único exemplo, reduzindo memorização extrema.

### Probabilidade e decisão

A floresta retorna a probabilidade da classe positiva:

```text
p_fraude = predict_proba(registro)[classe 1]
```

A decisão binária é:

```text
predictedFraud = p_fraude >= threshold
```

O threshold padrão é `0.5`, configurável por `FRAUD_DECISION_THRESHOLD`.

Exemplo:

```text
p_fraude = 0,82
threshold = 0,50

0,82 >= 0,50 -> predictedFraud = true
riskLevel = HIGH
classification = "Suspicious transaction"
```

Um caso de `0,42` com limiar `0,50` recebe `predictedFraud = false`, mas `riskLevel = MEDIUM`. Isso não é contradição: a decisão usa o limiar do modelo, enquanto a faixa de risco usa cortes fixos de apresentação.

## Modelo não supervisionado: Isolation Forest

### Quando é usado

É escolhido quando nenhuma coluna alvo é encontrada. Exige pelo menos 8 linhas.

### Hiperparâmetros implementados

```text
IsolationForest
  n_estimators = 300
  contamination = 0.05
  random_state = 42
  n_jobs = -1
```

`contamination` é a proporção esperada de anomalias no conjunto de treinamento. O valor padrão é 5% e pode ser alterado por `FRAUD_ANOMALY_CONTAMINATION`.

### Conversão do score bruto

No `IsolationForest`, valores menores de `decision_function` representam observações mais anômalas. O projeto transforma esse valor em uma escala crescente de risco.

Durante o treino:

```text
low  = percentil 5 dos scores brutos
high = percentil 90 dos scores brutos
```

Na inferência:

```text
anomaly_score = (high - raw_score) / (high - low)
anomaly_score = limitar(anomaly_score, 0, 1)
```

Exemplo:

```text
low = -0,20
high = 0,10
raw_score do caso = -0,14

anomaly_score = (0,10 - (-0,14)) / (0,10 - (-0,20))
anomaly_score = 0,24 / 0,30 = 0,80
```

O resultado será risco alto de anomalia. Mesmo assim:

```text
predictedFraud = null
scoreType = ANOMALY_SCORE
classification = "High anomaly risk - review required"
```

O limiar armazenado para esse artefato é `0.7`, mas a implementação deliberadamente não transforma o score em uma afirmação binária de fraude.

## Faixas de risco

As faixas são iguais para os dois tipos de score:

| Regra | `riskLevel` |
|---|---|
| `score >= 0.70` | `HIGH` |
| `0.30 <= score < 0.70` | `MEDIUM` |
| `score < 0.30` | `LOW` |

O score é limitado ao intervalo `[0, 1]` e arredondado para quatro casas decimais antes de ser enviado.

## Como o sistema explica “por que deu esse resultado”

Para cada atributo esperado pelo modelo, o serviço cria uma cópia do registro e remove somente aquele valor, substituindo-o por ausente. O pipeline então imputa uma referência e recalcula o score.

Para uma variável `x`:

```text
impacto(x) = score_original - score_com_x_ausente
```

- impacto positivo: o valor presente aumentou o risco em relação à referência imputada;
- impacto negativo: o valor presente reduziu o risco;
- impacto absoluto menor que `0.0005` é descartado;
- apenas os 3 maiores impactos absolutos são retornados.

Exemplo:

```text
score original                  = 0,820
score sem valor_claim           = 0,710
impacto de valor_claim          = +0,110

Motivo: "Valor claim aumentou o risco estimado em aproximadamente 11,0 pontos percentuais."
```

### O que essa explicação significa

Ela é uma análise local de sensibilidade: informa quanto a previsão mudou ao substituir uma variável pela referência do pipeline.

Ela não significa que:

- a variável causou a fraude;
- o impacto seria igual em outro sinistro;
- a soma dos impactos recompõe exatamente o score;
- o método é equivalente a SHAP ou a uma explicação causal.

Quando nenhuma variável altera materialmente o valor, o resultado informa que não houve impacto isolado relevante.

## Explicação textual

Por padrão, o serviço monta um texto determinístico com risco, score, motivos locais e aviso de revisão humana.

Se a integração com Ollama estiver habilitada, ela recebe somente:

- tipo do score;
- nível de risco;
- score e limiar;
- classificação;
- motivos locais já calculados.

O prompt exige até três frases, proíbe inventar causas e recomenda revisão. Os atributos brutos e o identificador do cliente não são enviados. Se o gerador falhar, o score continua sendo entregue com uma explicação determinística do tipo `FALLBACK`.

O modelo generativo nunca muda `probability`, `predictedFraud`, `riskLevel` ou `threshold`.

## Métricas de treinamento supervisionado

Considere:

- `TP`: previu fraude e era fraude;
- `FP`: previu fraude e era legítimo;
- `TN`: previu legítimo e era legítimo;
- `FN`: previu legítimo e era fraude.

### Acurácia

```text
accuracy = (TP + TN) / (TP + FP + TN + FN)
```

Mostra a proporção total de acertos. Pode enganar em datasets muito desbalanceados.

### Precisão

```text
precision = TP / (TP + FP)
```

Entre os casos marcados como fraude, informa quantos realmente eram fraude. Precisão baixa significa muitos falsos alarmes.

### Recall ou sensibilidade

```text
recall = TP / (TP + FN)
```

Entre as fraudes reais, informa quantas foram encontradas. Recall baixo significa fraude escapando como legítima.

### F1

```text
F1 = 2 * precision * recall / (precision + recall)
```

Resume o equilíbrio entre precisão e recall.

### PR-AUC e ROC-AUC

- PR-AUC mede a qualidade da relação precisão-recall em vários limiares e costuma ser informativa em fraude rara.
- ROC-AUC mede a capacidade de ordenar positivos acima de negativos em vários limiares.

As métricas ficam associadas à versão do modelo. Elas não devem ser comparadas diretamente com o dashboard sem observar que o dashboard usa os casos já processados e confirmados em produção, enquanto o treinamento usa o holdout do dataset.

## Matriz de confusão do dashboard

O dashboard inclui somente linhas Gold onde `predicted_fraud` e `confirmed_fraud` não são nulos.

| Real / Previsto | Fraude | Legítimo |
|---|---:|---:|
| Fraude | TP | FN |
| Legítimo | FP | TN |

O backend calcula acurácia, precisão, recall e F1 com as fórmulas anteriores. Divisões sem denominador retornam `0`, não `NaN`.

Casos de anomalia ficam fora dessa matriz porque `predicted_fraud = null`. Casos ainda não revisados também ficam fora porque `confirmed_fraud = null`.

## Métricas do gráfico de decisões

Para a categoria selecionada (`predictedFraud=true` ou `false`), o backend calcula diretamente no PostgreSQL:

- quantidade total;
- percentual sobre todos os registros Gold;
- média de `risk_score`;
- distribuição LOW/MEDIUM/HIGH;
- versão de modelo predominante por contagem;
- quatro motivos mais frequentes extraídos de `reasons`.

O percentual de fraudes previsto no resumo é:

```text
predictedFraudPercentage = predictedFraudsByAi * 100 / totalGold
```

Como o denominador inclui previsões pendentes, a soma de “fraude” e “legítimo” pode ser menor que 100% quando existem resultados com `predictedFraud = null`.

## Versão e artefato

A versão segue o padrão:

```text
supervised-rf-AAAAMMDDhhmmss-<8 primeiros caracteres do SHA-256>
anomaly-iforest-AAAAMMDDhhmmss-<8 primeiros caracteres do SHA-256>
```

O candidato é salvo em Joblib e recebe um manifesto `.metadata.json` com versão, tipo, data, hash, alvo, limiar, features e métricas. A gravação usa arquivo temporário e substituição atômica.

Na ativação, o candidato é carregado e salvo no caminho do artefato ativo. O worker de inferência percebe a alteração do arquivo e recarrega o bundle.

### Limitação atual do registro Java

Ao registrar o modelo ativo em `ml.model_registry`, o backend atual grava `feature_schema = {}` e `threshold = null`, pois o evento de resultado do treinamento consumido pelo Java não carrega esses campos. O artefato Joblib possui os valores corretos e é a fonte usada na inferência. Uma melhoria importante é ampliar o contrato Kafka para persistir também schema e threshold no registro SQL.

## Por que um registro recebe determinado resultado

Para investigar um caso, siga esta ordem:

1. verifique `scoreType` para saber o significado do score;
2. confira `modelVersion`, porque modelos diferentes podem aprender relações diferentes;
3. compare `probability` com `threshold` para entender a decisão supervisionada;
4. confira a faixa fixa que produziu `riskLevel`;
5. leia `reasons` como sensibilidades locais, não como causas;
6. compare `realFraud` com `predictedFraud` somente quando ambos existirem;
7. consulte os atributos normalizados em `features` e a origem em Bronze para auditar transformações;
8. use a revisão humana para consolidar a verdade operacional.

O modelo aprende relações estatísticas existentes no dataset de treinamento. O repositório não contém regras de negócio fixas do tipo “valor acima de X é fraude”; portanto, sem conhecer o artefato e os dados treinados, não existe uma fórmula manual única por atributo capaz de reproduzir a Random Forest.

