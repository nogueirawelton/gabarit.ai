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

## Fase 1: modelo de concursos (pacote `exam`)

- [ ] **`src/main/resources/db/migration/V1__create_exam.sql`**:
  - `exam`: id, `source`, `external_id`, `title`, `url`, `uf`, `region`, `education_level`, `openings`, `registration_start`, `registration_end`, `registration_note`, `source_updated_at` (`timestamptz`, o `occurred_at` do último evento aplicado), `created_at`, `updated_at`.
  - **`UNIQUE (source, external_id)`**: é a identidade do concurso, e o upsert depende disso.
  - `exam_position`: id, `exam_id` (FK), `name`, `UNIQUE (exam_id, name)`.
  - **Pesquisar:** *Flyway versioned migrations naming*, *por que nunca editar migration já aplicada*, *timestamptz vs timestamp*.
- [ ] **Entidades JPA** `Exam` e `ExamPosition` (1:N, cargo só existe dentro do concurso).
  - **Pesquisar:** *@OneToMany orphanRemoval*, *FetchType.LAZY e N+1*, *equals/hashCode em entidade JPA*.
  - **Decisão sua:** cargo que some do evento (o concurso foi retificado) → apagar ou marcar inativo? Se planos vão referenciar cargo, marque inativo: apagar quebra o plano.
- [ ] **`ExamRepository`** com busca por `(source, externalId)`.
- **Pronto quando:** `make test s=core` sobe, o Flyway aplica a V1 e o Hibernate valida as entidades contra o schema.

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
- [ ] **`ExamService.upsert(evento)`** com `@Transactional`:
  - não existe → cria; existe → atualiza **só se `occurred_at` > `source_updated_at`** (evento atrasado ou reentregue não sobrescreve dado mais novo);
  - cargos: sincroniza com a lista do evento (o evento traz o estado completo).
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
