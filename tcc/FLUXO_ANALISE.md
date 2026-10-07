# Fluxo de análise

A documentação anterior descrevia apenas a consulta ao dashboard e não
representava mais o fluxo de ingestão assíncrona.

A documentação vigente está em [ARCHITECTURE.md](./ARCHITECTURE.md) e cobre:

- upload de CSV/XLS/XLSX;
- Spring Batch;
- camadas Bronze, Silver e Gold;
- colunas dinâmicas;
- transactional outbox e Kafka;
- evidências locais e explicação generativa opcional com fallback;
- registros rejeitados;
- confirmação humana de fraude;
- APIs de consulta e filtros.
