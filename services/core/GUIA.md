# Guia do core

Roteiro do serviço, na ordem de implementação. Cada item diz **o que fazer**, **o que pesquisar** e **quando está pronto**. Marque o checkbox ao terminar e peça revisão ao fim de cada fase.

Itens com 🛠️ são infra: peça para o Claude fazer.

**Papel:** o `core` é todo o backend: API do web, concursos, planos de estudo, usuários, notificações. Fora dele ficam só os workers Python (`scraper`, `extraction`), ligados por fila.

**Primeiro fluxo:** `scraper` publica `exam.discovered` → fila `core.exam_discovered` → o `core` faz upsert do concurso e dos cargos → a API expõe para o web.

**Organização por assunto, não por camada:**
```
com.gabarit.core
├── exam/      tudo de concurso: entidade, repositório, serviço, listener, controller, DTOs
├── plan/      planos de estudo (depois)
├── user/      usuários (depois)
└── shared/    config, tratamento de erro, utilitários
```
Regra: um pacote usa o outro pelo serviço público dele (ex.: `ExamService`), nunca pelo repositório.

**Como rodar:**
- `make up`: stack inteira, `core` em `http://localhost:8080` (dev: `make restart s=core` recompila).
- **Ciclo rápido:** rode `CoreApplication` pela IDE no host com `CORE_DB_PASSWORD`, `RABBITMQ_USER` e `RABBITMQ_PASSWORD` no ambiente; os defaults do `application.yml` apontam para `localhost` (portas publicadas pelo override).
- Sem stack nenhuma: `TestCoreApplication` (src/test) sobe Postgres e RabbitMQ com Testcontainers.
- `make test s=core`: testes no container, com Testcontainers.

---

## Fase 0: preparação ✅

- [x] 🛠️ Esqueleto (Spring Initializr): Java 25, Spring Boot 4.1, Web MVC, Validation, Data JPA, Flyway, PostgreSQL, Spring for RabbitMQ, Actuator, Testcontainers.
- [x] 🛠️ `application.yml` lendo tudo de variável; `ddl-auto: validate` (schema é do Flyway) e `open-in-view: false`.
- [x] 🛠️ `Dockerfile` multi-stage (`dev` com JDK, `prod` com JRE + jar em camadas, sem root), serviço no compose com healthcheck em `/actuator/health/readiness`.
- [x] 🛠️ Testcontainers com as mesmas imagens do compose (`pgvector/pgvector:0.8.0-pg16`, `rabbitmq:3.13.7-management`).
- [x] 🛠️ CI: `./mvnw verify`.

---

## Fase 1: modelo de concursos (pacote `exam`) ✅

- [x] **`db/migration/V1__create_exam.sql`**: `exam` com `UNIQUE (source, external_id)` e `source_updated_at` (`occurred_at` do último evento aplicado); `exam_position` com `UNIQUE (exam_id, name)` e `active`.
- [x] **`ExamSnapshot`**: estado completo do concurso vindo da fonte (o evento vira isto antes de chegar na entidade).
- [x] **`Exam`** (entidade): só muda por `Exam.create(snapshot, occurredAt)` e `exam.apply(snapshot, occurredAt)`.
  - `apply` ignora evento mais antigo ou do mesmo instante (devolve `false`) e recusa snapshot de outro concurso;
  - cargos: cria os novos, **inativa os que sumiram** (não apaga: planos vão referenciar cargo) e reativa os que voltaram.
- [x] **`ExamPosition`**: só o `Exam` cria/ativa/inativa (métodos package-private).
- [x] **`ExamRepository`** (package-private): `findBySourceAndExternalId` já traz os cargos (`@EntityGraph`).
- [x] Testes: `ExamTest` (regras, sem banco) e `ExamRepositoryTest` (`@DataJpaTest` no Postgres real com as migrations).

> ✋ **Revisão:** implementada pelo Claude. Leia `Exam.apply`/`syncPositions` e pergunte o que não estiver claro.
>
> Detalhe que pega na Fase 2: com `GenerationType.IDENTITY` o `INSERT` sai já no `save()` (o Hibernate precisa do id), então violação de unique estoura no `save`, não no fim da transação.

---

## Fase 2: consumir `exam.discovered`

- [ ] **DTO do evento** (`record`) espelhando `contracts/events/exam.discovered.v1.json`: envelope (`event_id`, `event_type`, `version`, `occurred_at`, `correlation_id`) + `data`.
  - `occurred_at` → `Instant`; datas de inscrição → `LocalDate`; `positions` → lista de `record` com `name`.
  - **Pesquisar:** *Jackson 3 no Spring Boot 4 (pacote `tools.jackson`)*, *JacksonJsonMessageConverter do Spring AMQP*, *Java records com Jackson*.
- [ ] **Teste de contrato:** um JSON de exemplo valida contra o schema em `contracts/` e desserializa no DTO (no container de dev, `contracts/` é montado em `/contracts`; no host, suba diretórios até achar).
  - **Pesquisar:** *networknt json-schema-validator draft 2020-12*.
- [ ] **`ExamDiscoveredListener`** com `@RabbitListener(queues = "core.exam_discovered")`.
  - **Não declare a fila no código** (`@Bean Queue`): ela é da infra (`infra/rabbitmq/definitions.json`). Declarar com argumento diferente derruba o listener com `PRECONDITION_FAILED`.
  - `version` diferente de 1 ou payload inválido → `AmqpRejectAndDontRequeueException` (vai direto pra DLQ).
  - Erro transitório (banco fora) → deixe a exceção subir: volta pra fila e, após 3 entregas, vai pra DLQ (`x-delivery-limit`).
- [ ] **`ExamService.upsert(snapshot, occurredAt)`** com `@Transactional`: busca por `(source, externalId)`; não existe → `Exam.create` + `save`; existe → `exam.apply` (a regra de evento atrasado e a sincronização dos cargos já estão na entidade).
  - o listener traduz o DTO do evento para `ExamSnapshot` e chama o serviço.
  - **Pesquisar:** *event-carried state transfer*, *idempotent consumer*, *INSERT ... ON CONFLICT vs buscar-e-salvar*, *@Transactional em método chamado da mesma classe (self-invocation)*.
- [ ] **Teste de integração** (Testcontainers): publica no `gabarit.events` → o concurso aparece no banco; publicar o mesmo evento de novo não duplica; evento mais antigo não sobrescreve.
  - O RabbitMQ do Testcontainers vem vazio: o teste precisa criar a topologia (importe o `definitions.json` ou declare no setup do teste).
- **Pronto quando:** `make up` + `make scrape` → os ~600 concursos estão no banco, a fila `core.exam_discovered` esvazia e rodar `make scrape` de novo não duplica nada.

---

## Fase 3: API de leitura

- [ ] `GET /exams`: paginado, filtros por `uf`, inscrições abertas (calculado pelas datas, nunca guardado), texto no título.
- [ ] `GET /exams/{id}` com os cargos.
- [ ] DTOs de resposta separados das entidades; erros no formato `ProblemDetail` (RFC 9457) num `@RestControllerAdvice` em `shared/`.
  - **Pesquisar:** *Pageable e Page no Spring Data*, *Specification ou query derivada para filtros opcionais*, *por que não devolver entidade JPA no controller*.
- **Pronto quando:** o web consegue listar e abrir concursos, e uma consulta com N concursos faz um número fixo de queries (sem N+1).

---

## Depois

- **`plan/`**: plano de estudo por usuário e cargo-alvo.
- **`user/`**: cadastro e autenticação (Spring Security).
- **Notificações**: job assíncrono no próprio `core` (ex.: concurso novo para o cargo que o usuário segue).
- **Normalização de cargos entre bancas**: o scraper manda o nome cru; padronizar é papel do `core`.

---

## Erros clássicos para evitar

- `ddl-auto: update` / `create`: o schema é das migrations. Mudou entidade → migration nova.
- Editar uma migration já aplicada (o Flyway recusa o checksum; em produção, dor de cabeça).
- `LocalDateTime` para instante de evento: use `Instant` / `timestamptz`.
- Devolver entidade JPA no controller (lazy loading estoura fora da transação, e o schema do banco vaza na API).
- `@Transactional` em método `private` ou chamado de dentro da própria classe: o proxy não intercepta, e não há transação.
- Declarar a fila `core.exam_discovered` no código com argumentos diferentes da infra.
- Pegar a exceção no listener e só logar: a mensagem recebe `ack` e some. Deixe subir, ou rejeite explicitamente.
- Misturar Jackson 2 (`com.fasterxml.jackson.databind`) com Jackson 3 (`tools.jackson.databind`) nos imports.
