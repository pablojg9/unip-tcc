# Fraud API architecture

## Scope

The Java service owns dataset ingestion, medallion persistence, orchestration of
fraud scoring through Kafka, query APIs and human review. It does not train or
execute the machine-learning model.

## Dependency direction

```text
adapter/in (HTTP, Batch, scheduler, Kafka consumer)
                  |
                  v
application/port/in -> application/service -> application/domain
                  |
                  v
application/port/out
                  ^
                  |
adapter/out (PostgreSQL, file storage, Kafka producer)
```

Application ports do not expose JPA entities, Spring Data `Page` objects,
`MultipartFile` or Kafka classes. Controllers translate transport data into
application commands, and persistence adapters translate domain objects into
entities.

## Main patterns

- Hexagonal architecture for dependency inversion.
- Strategy + Factory for CSV/XLS/XLSX readers.
- Spring Batch Reader/Processor/Writer for chunked and restartable imports.
- Repository adapters for each medallion responsibility.
- MapStruct for compile-time mapping between domain objects and JPA entities.
- Transactional Outbox for reliable Silver-to-Kafka publication.
- Specification Factory for composable optional Gold filters.
- Domain methods for explicit entity state transitions instead of public setters.
- Global exception handler for a consistent HTTP error contract.

## Import flow

```text
POST /imports
  -> validate command
  -> store file and calculate SHA-256
  -> reject duplicate active/completed import
  -> create ops.import_job (QUEUED)
  -> launch datasetImportJob asynchronously
  -> read CSV/XLS/XLSX with the matching strategy
  -> normalize names and values
  -> write raw row to Bronze
  -> write normalized row to Silver or quarantine it in ops
  -> register the detected schema
  -> enqueue a scoring event in the transactional outbox
  -> publish pending outbox events to Kafka
```

The `importId + sheet + row` tuple produces a deterministic transaction ID.
Restarts therefore do not create a second claim.

## Fraud label semantics

The confirmed fraud column is optional. Its accepted aliases are configured by
`IMPORT_TARGET_ALIASES`.

- Missing target: `confirmedFraud = null` (unknown).
- Accepted positive label: `confirmedFraud = true`.
- Accepted negative label: `confirmedFraud = false`.
- Invalid target value: the row is quarantined with its original data.

Columns not recognized as the target are preserved as features. Adding a new
column does not require a Java code or database schema change.

## Scoring and generative explanation flow

```text
Silver/outbox -> Kafka -> Python model -> score and risk
                                      -> local per-claim evidence
                                      -> optional local Ollama explanation
                                      -> deterministic fallback when unavailable
                                      -> Kafka -> Gold -> transaction detail
```

The generative model never changes the fraud probability, threshold or
classification. It receives only the score metadata and local evidence
sentences, not the raw claim fields. Gold stores the generated text, its type
(`GENERATIVE`, `DETERMINISTIC` or `FALLBACK`) and the model name for audit.

## Medallion responsibilities

| Schema | Responsibility |
| --- | --- |
| `bronze` | Immutable original rows and detected column registry. |
| `silver` | Normalized, queryable claims and confirmed labels. |
| `gold` | Model score, risk level, model version and explanation. |
| `ops` | Import state, rejected rows and transactional outbox. |
| `review` | Immutable human review history. |
| `ml` | Model metadata prepared for later Python integration. |

JSONB keeps attributes dynamic while fixed operational columns preserve
traceability and indexing. Flyway owns all schemas; Hibernate only validates
the mapping.

## HTTP API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/imports` | Upload CSV/XLS/XLSX and enqueue a batch job. |
| `GET` | `/imports` | List import jobs. |
| `GET` | `/imports/{id}` | Read progress and final status. |
| `POST` | `/imports/{id}/retry` | Restart a failed Spring Batch execution. |
| `GET` | `/imports/{id}/schema` | Read dynamic columns, types and roles. |
| `GET` | `/imports/{id}/errors` | Read quarantined rows. |
| `GET` | `/claims` | Query normalized claims with server pagination. |
| `GET` | `/claims/{transactionId}` | Read a normalized claim. |
| `POST` | `/claims/{transactionId}/reviews` | Confirm fraud, legitimacy or inconclusive status. |
| `GET` | `/dashboard/summary` | Read aggregate counters. |
| `GET` | `/dashboard/results` | Query Gold scoring results. |

Dynamic claim filters use `field` and `value`; field names are restricted to
normalized identifiers and values are always bound SQL parameters.

## Import status

```text
QUEUED -> PROCESSING -> COMPLETED
                    -> COMPLETED_WITH_WARNINGS
                    -> FAILED
```

The file hash prevents accidental re-import of an active or completed file.
Failed files may be submitted again.

## Local configuration

Relevant environment variables:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `KAFKA_BOOTSTRAP_SERVERS`
- `KAFKA_TRANSACTIONS_TOPIC`, `KAFKA_RESULTS_TOPIC`
- `IMPORT_STORAGE_PATH`
- `IMPORT_MAX_FILE_SIZE`
- `IMPORT_TARGET_ALIASES`
- `OUTBOX_PUBLISH_DELAY_MS`
- `GENERATIVE_EXPLANATION_ENABLED`
- `GENERATIVE_EXPLANATION_URL`
- `GENERATIVE_EXPLANATION_MODEL`
- `GENERATIVE_EXPLANATION_TIMEOUT_SECONDS`

Run validation with:

```bash
./mvnw clean verify
```
