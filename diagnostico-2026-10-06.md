# Diagnóstico atualizado: monografia x repositório TCC (06/10/2026)

**Fontes**
- Monografia: a mesma versão de 28/09, `TCC_CC_Grupo_3_Diego_Entrega_Final.docx`. Nenhuma versão nova foi enviada.
- Código: github.com/MizeraviDoSertao/TCC, atualizado até o commit `ad219ed1` de 06/10 ("add fraud model evaluation and data quality"), mais a preparação genérica de datasets ainda não commitada. Li Java, Python, Angular, migrações SQL e docker-compose.
- Build e execução: a API Java compila e passa 17 testes, o Python passa 18 testes e o Angular compila. Os 5 containers principais sobem com `docker compose`; o Ollama é um perfil opcional para explicações generativas locais.

Substitui `monografia-x-codigo.md` (28/09). O texto da monografia continua com os problemas de `revisao-monografia-e-plano.md`, porque não mudou.

---

## 1. Resumo em 6 linhas

1. **O sistema evoluiu muito e hoje roda inteiro com `docker compose up --build`.** Isso não é mais risco.
2. **A monografia ficou mais desatualizada do que antes.** O cap. 3 descreve tabelas, entidades, fluxo e Código 1 que não existem mais.
3. **SMOTE foi implementado no experimento offline.** O modelo de produção continua usando `class_weight="balanced_subsample"` no Random Forest.
4. **Continua sem nenhum modelo treinado no fraud_oracle.** O único artefato salvo foi treinado numa planilha sintética de 30 linhas, com métricas 1,0 em tudo.
5. **Os baselines foram implementados no experimento offline** e a **IA generativa local foi implementada** para explicar cada sinistro sem alterar o score.
6. **Os capítulos 4, 5 e 6 seguem inexistentes.** O caminho crítico agora é só gerar os resultados no fraud_oracle e reescrever o texto.

---

## 2. O que mudou desde 28/09

### Resolvido ou melhorado no código
| Item de 28/09 | Situação em 06/10 |
|---|---|
| Só 9 das 33 colunas eram usadas | ✅ **Todas as colunas** viram features dinamicamente (JSONB). Adicionar coluna não exige mudar código |
| Split treino/teste sem estratificação | ✅ `train_test_split(..., stratify=target)`, teste de 25% |
| Sem ROC-AUC | ✅ ROC-AUC calculado, além de acurácia, precisão, recall e F1 |
| Valores ausentes não tratados | ✅ Imputação no pipeline Python: mediana (numéricos) e mais frequente (categóricos). Vazio vira nulo na Silver |
| `transactionId` aleatório, sem ligação com a Bronze | ✅ **Id determinístico** a partir de `importId + aba + linha`. A Bronze guarda `import_id` e `row_number`, então o rastreio Bronze→Silver→Gold existe de verdade |
| Sem autenticação | ⚠️ Spring Security com HTTP Basic, mas **só para treinar e ativar modelos** (`POST /models/**`, perfil `MODEL_ADMIN`). As demais rotas seguem abertas |
| Aprendizado não supervisionado inexistente | ✅ **Isolation Forest** treinado automaticamente quando a base não tem rótulo. O resultado vira "risco de anomalia" e exige revisão humana |
| Modelo com caminho fixo `/Users/pablo/...` | ✅ Treino por CLI, por Kafka ou pela tela "Modelos", com versionamento, hash SHA-256 do dataset e arquivo de metadados |
| Dashboard simples | ✅ 5 telas: resumo, explorar sinistros, importações (upload CSV/XLS/XLSX), gestão de modelos e detalhe da transação com **revisão humana** |
| Ingestão rodava uma vez ao subir | ✅ Upload pela tela, Spring Batch em lotes reiniciáveis, deduplicação **por arquivo** (hash), quarentena de linhas inválidas |
| Infra | ✅ Flyway com 6 schemas (`bronze`, `silver`, `gold`, `ops`, `review`, `ml`), Transactional Outbox, tópico de mensagens mortas (`transactions.DLT`), Dockerfile por serviço |

### Continua igual (não resolvido)
| Item | Situação |
|---|---|
| SMOTE | ✅ Existe no experimento offline com `imbalanced-learn`; produção permanece com peso de classe |
| Baseline LR e Árvore de Decisão | ✅ Implementados no experimento offline; produção permanece com Random Forest e Isolation Forest |
| Modelo treinado no fraud_oracle | ❌ O único artefato (`ml-fraud-py/artifacts/candidates/8f8c….joblib`) foi treinado em `sinistros_teste.xlsx`: **30 linhas sintéticas** em português, geradas por script, com **métricas 1,0** em tudo (o teste tinha cerca de 8 linhas) |
| Métricas para o cap. 4 | ⚠️ O código gera matriz de confusão, curvas ROC/PR, PR-AUC, limiares e comparação; ainda falta executá-lo na planilha oficial |
| IA generativa (3.5.2) | ✅ Evidências locais são calculadas por sinistro e um Ollama opcional gera o texto. A Gold registra texto, tipo e modelo; falhas usam fallback determinístico sem interromper o scoring |
| Gráficos no dashboard | ❌ Nenhum gráfico, matriz de confusão ou série temporal. Há contadores por nível de risco (baixo, médio, alto) |
| Regras de qualidade de domínio | ⚠️ O treinamento remove duplicatas e trata idade/calendário inválidos; a Silver ainda faz apenas normalização de nomes e tipos |

### Novos problemas que o código introduziu
1. ✅ **Identificadores foram removidos das features.** A lista é configurável e inclui `id_sinistro`, `numero_apolice`, `segurado_id`, `PolicyNumber` e `RepNumber`.
2. **O modelo de produção é re-treinado com 100% das linhas** depois de medir no teste (`supervised.py`). As métricas salvas são honestas, mas **se o mesmo arquivo for depois enviado em "Analisar", os scores mostrados no dashboard são de dados que o modelo já viu**.
3. **Dataset de teste diferente do da monografia.** O repositório traz uma base sintética brasileira (`sinistros_teste.xlsx`); a monografia fala só do fraud_oracle. O fraud_oracle não está no repositório.
4. **Sinais de código gerado por ferramenta de IA** (pasta `tcc/.codex_tmp/` com script que usa `@oai/artifact-tool`). Não é problema em si, mas o grupo precisa conseguir explicar cada parte para a banca.
5. Arquivos que não deveriam estar versionados: `fraud-dashboard/dist/`, `ml-fraud-py/.idea/`, `.DS_Store`.

---

## 3. O que está escrito mas não corresponde ao código (situação em 06/10)

Legenda: ✅ bate · ⚠️ bate em parte · ❌ não bate

| Monografia diz | Código faz | |
|---|---|---|
| Dashboard em Angular (3.3.3); Streamlit (Tab. 1); Power BI (3.4) | Angular 17 com nginx | ✅ / ❌ / ❌ |
| Metodologia (1.4): "aprendizado não supervisionado" | Supervisionado (RF) quando há rótulo; **Isolation Forest quando não há** | ⚠️ Agora dá para manter, explicando os dois modos |
| SMOTE no treino (2.4.5, 3.2.8, 3.4.6, Tab. 1) | SMOTE existe apenas no experimento offline; produção usa peso de classe `balanced_subsample` | ⚠️ Texto precisa separar experimento e produção |
| Regressão Logística como baseline; "quatro algoritmos" (2.4.4) | LR e Árvore existem no experimento offline; produção usa RF ou Isolation Forest | ⚠️ |
| Treino sobre a camada Silver (3.2.8) | Treino a partir de **arquivo** enviado na tela Modelos (CSV/XLS/XLSX), não lido da Silver | ❌ |
| 3 entidades JPA `BronzeTransacaoRawEntity`, `SilverTransacaoTratadaEntity`, `GoldResultadoFraudeEntity` (3.2.1, 3.4.2, Código 1) | Tabelas `bronze.claim_raw`, `silver.claim`, `gold.fraud_prediction`, mais `ops.import_job`, `ops.rejected_record`, `ops.transaction_outbox`, `review.fraud_review`, `ml.model_registry`, `ml.model_training`, `bronze.dataset_schema`. Atributos em **JSONB** | ❌ O Código 1 inteiro precisa ser trocado |
| `idTransacao` derivado do dataset liga Bronze→Gold (3.2.1, RNF06) | Id determinístico de `importId + aba + linha`; Bronze guarda `import_id` e `row_number` | ⚠️ Rastreio existe; descrever como é |
| Leitura automática de CSV (RF01, 3.4.3) | Upload pela tela de CSV, XLS e XLSX, processado por Spring Batch | ⚠️ |
| Silver remove duplicatas, trata ausentes, normaliza (3.4.4, RF03) | Silver normaliza nomes e tipos; deduplica **arquivos** inteiros. Duplicatas de linha, valores inválidos e ausentes são tratados no Python antes do treino | ⚠️ |
| Tópicos `transacoes` / `resultado-fraude` (3.4.5) | `transactions`, `fraud-results`, `transactions.DLT` e 4 tópicos de ciclo de vida de modelo | ⚠️ |
| Classificação "FRAUDE"/"LEGÍTIMO" + probabilidade | Probabilidade, nível de risco (LOW <0,3 ≤ MEDIUM <0,7 ≤ HIGH), limiar 0,5, "Suspicious/Normal transaction", versão do modelo, `reasons` | ⚠️ Texto precisa incluir nível de risco e versão do modelo |
| Autenticação nos endpoints (RNF03) | HTTP Basic só em treino e ativação de modelo | ⚠️ |
| IA generativa explica cada sinistro (3.5.2) | Existe como perfil local Ollama, alimentado apenas por evidências locais, score, risco e limiar | ✅ Texto precisa descrever a implementação real e o fallback |
| Gráficos, matriz de acertos, evolução temporal, ranking por risco (3.5.1) | Contadores e tabelas com filtro; sem gráficos | ❌ |
| Java 21, Spring Boot 3 (3.1.2) | Java 21, **Spring Boot 4.0.6**, Spring Batch, Spring Security, Flyway, Apache POI, Commons CSV, MapStruct, Lombok | ⚠️ |
| Bibliotecas Python da Tab. 1 | pandas, scikit-learn, joblib, kafka-python, openpyxl, xlrd | ❌ |
| Arquitetura Hexagonal (3.2.1) | Hexagonal de fato: `port/in`, `port/out`, `application/service`, `adapter/in`, `adapter/out`; também no Python | ✅ |
| Kafka entre Java e Python | Sim, com outbox, retry e DLT | ✅ |
| Medalhão Bronze/Silver/Gold no PostgreSQL | Sim, em schemas separados, mais `ops`, `review` e `ml` | ✅ |
| Não mencionado na monografia | Revisão humana, registro e ativação de modelos, quarentena, outbox, upload de Excel, Docker Compose, fallback auditável e testes automatizados (17 Java + 18 Python) | ➕ Material novo para o cap. 3 |

**Consequência:** o cap. 3 (seções 3.1.1, 3.1.2, 3.2.1, 3.2.6 a 3.2.10, 3.3 e 3.4 inteiras, 3.5) precisa de reescrita, não de correção pontual. É o maior item de texto depois dos caps. 4 a 6.

---

## 4. O que já está pronto, incompleto e faltando

**Pronto**
- Sistema completo e executável com um comando, com testes automatizados.
- Caps. 1 e 2 (com as correções pontuais já listadas em 28/09).
- Figuras 1 e 2 podem ser refeitas a partir de `tcc/ARCHITECTURE.md`, que já descreve o fluxo.

**Incompleto**
- Cap. 3: descreve a versão antiga do sistema.
- Avaliação do modelo: código pronto e validado com a planilha sintética, mas ainda não rodado na planilha oficial.
- Referências: duplicadas, fora de ordem, ~10 não citadas (sem mudança).

**Faltando**
- Caps. 4 Resultados, 5 Discussão, 6 Conclusão e Trabalhos Futuros.
- Resumo, Abstract, listas, folha de aprovação correta, ficha catalográfica.
- Experimento comparativo (pergunta de pesquisa: bruto x tratado; SMOTE x peso de classe x nada; RF x baselines).

---

## 5. Decisões para o grupo (esta semana)

★ = recomendação

| # | Decisão | Recomendação |
|---|---|---|
| D1 | SMOTE: implementar no sistema ou mudar o texto? | ★ **Manter o peso de classe no sistema** e rodar SMOTE só no experimento offline (script à parte). O cap. 4 compara os dois e justifica a escolha. O cap. 2 continua válido |
| D2 | Baseline LR e Árvore | ★ Só no experimento offline. Não precisa entrar no sistema |
| D3 | IA generativa (3.5.2) | ✅ **Implementada localmente com Ollama.** Descrever evidências locais, explicação generativa, fallback determinístico e revisão humana obrigatória |
| D4 | Dataset do cap. 4 | ★ **fraud_oracle**. A planilha sintética brasileira só aparece como teste funcional do sistema |
| D5 | Como evitar scores de dados já vistos | ★ Separar o fraud_oracle em `treino.csv` (75%) e `teste.csv` (25%, estratificado) **antes** de subir. Treinar em Modelos com o treino, analisar em Analisar só o teste. Zero mudança de código |
| D6 | Identificadores como feature | ★ Tirar `PolicyNumber` e `RepNumber` do arquivo antes de treinar, e registrar isso no cap. 4 como etapa de tratamento. Opcional: lista de colunas ignoradas no Python |
| D7 | Gráficos no dashboard | ✅ **Implementar.** Adicionar visualizações de risco e desempenho ao dashboard, mantendo matriz de confusão e curvas ROC/PR também nos resultados offline |
| D9 | Limiar de decisão (fixo em 0,5 no código) | ★ Escolher no experimento offline pela curva precisão-recall; se for bem diferente de 0,5, ajustar `threshold` no `supervised.py` antes de congelar |
| D8 | Congelar o código | ★ **Congelar até 18/10** (tag `v1.0-tcc`). Depois disso só correção de bug. Cada mudança nova aumenta o texto a reescrever |

---

## 6. Plano de 06/10 a 12/11

Faltam 5 semanas até a entrega e 3,5 semanas até a meta interna de 31/10. Se esta semana ainda for de férias, ela concentra o trabalho técnico.

### Semana 1 · 06/10 – 11/10 · Resultados reais (caminho crítico)
| Tarefa | Resp. | Pronto quando |
|---|---|---|
| Decidir D1–D9 | todos | Decisões registradas |
| Confirmar `featureCount`/`targetColumn` em `model_management.py` | ✅ | Implementado no commit `ad219ed1`; a tela Modelos mostra o número de variáveis |
| Script offline genérico: identificadores, split estratificado 75/25 e 5-fold apenas no treino | ✅ | Aceita CSV/XLS/XLSX, múltiplas abas e coluna-alvo configurável; falta rodar na planilha oficial |
| Modelos: Regressão Logística, Árvore de Decisão, Random Forest | ✅ | Implementados no experimento offline |
| Desbalanceamento: nenhum x peso de classe x SMOTE (`imblearn.pipeline`, só no treino) | ✅ | Implementado |
| Tratamento: bruto x tratado (contar e tratar `Age = 0`, mês/dia "0", duplicatas, ids) | ✅ | Implementado |
| Salvar `results/`: tabela de métricas, matrizes de confusão, curvas ROC/PR, importância e limiares | ⚠️ | Gera todos os artefatos; falta executar na planilha oficial e guardar os resultados definitivos |
| No sistema: gerar `treino.csv`/`teste.csv` (D5, D6), treinar e ativar o modelo em Modelos, analisar o teste (~3.900 linhas, cerca de 7 min a ~9 sinistros/s; a base inteira levaria ~30 min), tirar prints de todas as telas e do banco | ____ | Prints + métricas do sistema batendo com o script (mesma faixa) |

### Semana 2 · 12/10 – 18/10 · Reescrever o cap. 3 e começar o cap. 4
- Reescrever 3.1.1 (tabela de tecnologias real: Java 21, Spring Boot 4.0.6, Spring Batch, Security, Flyway, Kafka, PostgreSQL 16, Python 3.11, pandas, scikit-learn, Angular 17, Docker), 3.1.2, 3.2.1, 3.2.6 a 3.2.10.
- Reescrever 3.3 e 3.4 a partir de `tcc/ARCHITECTURE.md` e `ml-fraud-py/README.md`: importação em lotes, Bronze/Silver/Gold/ops/review/ml, outbox, treino e ativação de modelos, supervisionado x anomalia, revisão humana.
- Trocar o Código 1 pelo DDL de `V1__create_medallion_schemas.sql` (trecho) ou pelo `SupervisedTrainingStrategy`.
- Refazer Figuras 1 e 2.
- Reescrever 3.5 com o dashboard real e atualizar 3.5.2 com a explicação generativa local e seu fallback.
- Implementar e documentar os gráficos do dashboard (distribuição de risco, desempenho do modelo e evolução temporal, conforme os dados disponíveis).
- Correções de 28/09 nos caps. 1–2: 1.4 (dois modos de aprendizado), 2.4.4 (três algoritmos + subseção do RF), referências cruzadas, typos.
- Começar cap. 4 com os números da semana 1.
- **Congelar o código no fim da semana (D8).**

### Semana 3 · 19/10 – 25/10 · Caps. 4, 5 e 6
- Cap. 4: perfil de qualidade do dataset; funcionamento do pipeline (contagens por camada, tempo); comparação de modelos; SMOTE x peso de classe; bruto x tratado; importância e limiar; telas do sistema.
- Cap. 5: resposta à pergunta de pesquisa; comparação com Özaltin e Karadağ Erdemir (2025), que usaram o mesmo dataset (CarClaims = fraud_oracle); trade-off de limiar; limitações.
- Cap. 6: retomar cada objetivo de 1.3. Trabalhos futuros: explicações locais mais sofisticadas (ex.: SHAP), dados brasileiros reais, autenticação completa e re-treino com revisões humanas.

### Semana 4 · 26/10 – 31/10 · Fechar a versão completa
- Resumo e Abstract com palavras-chave; listas; folha de aprovação; sumário.
- Referências: remover duplicatas, ordem alfabética, citar ou remover as ~10 sobrando, incluir scikit-learn, Spring Batch, Kafka, Isolation Forest (Liu; Ting; Zhou, 2008) e SMOTE (Chawla et al., 2002).
- Formatação pelo Template 2. Pedir ficha catalográfica.
- **Sábado 31/10: enviar ao orientador.**

### Semana 5 · 01/11 – 08/11 · Orientador
- Aplicar correções. Ensaiar a explicação do código por módulo (cada membro explica uma parte).

### Final · 09/11 – 12/11
- Ficha catalográfica, PDF final, conferência por duas pessoas. **Entrega 12/11.**

---

## 7. Riscos de não entregar

| Risco | Nível | Mudou desde 28/09? | Sinal de alerta | O que fazer |
|---|---|---|---|---|
| Sem resultados no fraud_oracle → sem cap. 4 | **Alto** | Igual | Sem `results/` até 09/10 | Script offline primeiro; o sistema só fornece prints. Se apertar, pular validação cruzada e tabela de limiares |
| Cap. 3 descreve um sistema que não existe mais | **Alto** | **Piorou** (o código mudou muito) | Cap. 3 não reescrito até 18/10 | Usar `ARCHITECTURE.md` e os READMEs como roteiro; congelar código (D8) |
| Texto mistura SMOTE/baselines offline com produção e descreve uma IA generativa diferente da implementada | **Alto** | **Melhorou no código** | Ainda no texto em 25/10 | Separar experimento de produção e reescrever 3.5.2 conforme Ollama + evidências locais + fallback |
| Pouco tempo até 31/10 (3,5 semanas) | **Alto** | **Novo** (passou uma semana sem avanço no texto) | Caps. 4–6 sem rascunho em 25/10 | Mandar rascunho dos caps. 4–6 ao orientador já em 25/10 |
| Scores do dashboard sobre dados de treino | Médio | Igual (mudou a causa: re-treino com 100%) | Mesmo arquivo usado em treino e análise | D5: arquivos separados |
| Recall baixo com limiar fixo 0,5 e 6% de fraude | Médio | **Novo** | Recall da classe fraude muito abaixo da precisão | D9 |
| Banca perguntar sobre partes do código que o grupo não domina | Médio | **Novo** | Ninguém consegue explicar outbox, Batch ou Isolation Forest | Cada membro estuda e explica um módulo na semana 5 |
| Novas features no código consumindo o tempo do texto | Médio | **Novo** | Commits novos depois de 18/10 | D8 |
| Build e execução | Baixo | **Melhorou** (verificado em 06/10) | — | — |
| Ficha catalográfica e retorno do orientador | Baixo | Igual | Sem resposta até 05/11 | Pedir a ficha na semana 4 |
