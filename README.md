# FraudGuard

Plataforma de análise de fraude automotiva com dashboard Angular, API Spring
Boot, processamento assíncrono por Kafka e modelos de machine learning em
Python.

## Executar o projeto

Requisitos: Docker com Docker Compose.

```bash
docker compose up --build
```

Depois, acesse [http://localhost:4200](http://localhost:4200). O Compose inicia
automaticamente o frontend, backend, PostgreSQL, Kafka e o serviço Python.

Para encerrar:

```bash
docker compose down
```

## Fluxos separados

- **Analisar:** recebe arquivos operacionais e calcula o risco usando o modelo
  ativo. Esse fluxo nunca retreina o modelo.
- **Modelos:** recebe um dataset histórico, treina uma versão candidata e exibe
  suas métricas. Apenas depois de uma ativação administrativa ela passa a ser
  usada nas análises. Bases rotuladas geram um modelo supervisionado; bases sem
  rótulo geram um detector de anomalias que exige revisão humana.

Por padrão, o ambiente local usa o usuário `admin` e a senha `admin-local`.
Defina credenciais próprias antes de iniciar um ambiente compartilhado:

```bash
MODEL_ADMIN_USER='gestor' MODEL_ADMIN_PASSWORD='uma-senha-forte' docker compose up --build
```

O backend exige a função administrativa nos pedidos de treinamento e ativação.
O frontend não persiste as credenciais no navegador.

## Avaliação offline dos modelos

O serviço Python inclui um experimento reproduzível que aceita qualquer CSV,
XLS ou XLSX rotulado. Ele compara Regressão Logística, Árvore de Decisão e
Random Forest, com peso de classe, SMOTE ou sem balanceamento, sem alterar o
modelo ativo:

```bash
cd ml-fraud-py
python -m fraud_detection split --dataset ../sinistros.xlsx --output-directory split-data
python -m fraud_detection evaluate --dataset ../sinistros.xlsx --output-directory results
```

O comando `split` cria `train.csv` e `test.csv` estratificados. Use
`--target-column "Nome da coluna"` quando a coluna de fraude tiver um nome não
reconhecido e repita `--ignore-column` para identificadores específicos da base.

A avaliação escolhe a configuração somente por validação cruzada no treino e
usa o teste intocado uma única vez. Os resultados incluem métricas, matrizes de
confusão, curvas ROC e precisão-recall, comparação de limiares e importância das
variáveis.

## Explicações generativas locais

Cada resultado possui evidências locais calculadas especificamente para o
sinistro. Opcionalmente, um modelo Ollama transforma essas evidências em uma
explicação curta em português, sem receber os campos brutos da planilha e sem
alterar o score do modelo de fraude.

```bash
docker compose --profile generative-ai up -d ollama
docker compose --profile generative-ai exec ollama ollama pull llama3.2:1b
GENERATIVE_EXPLANATION_ENABLED=true docker compose --profile generative-ai up --build
```

Se o modelo local estiver desligado ou indisponível, o sistema grava uma
explicação determinística e continua processando normalmente.
