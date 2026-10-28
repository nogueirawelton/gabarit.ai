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

## Fase 2: consumir `exam.discovered` ✅

- [x] **`ExamDiscoveredEvent`** (`record`, Jackson 3 com `@JsonNaming` snake_case): espelha o contrato; campo extra é ignorado (leitor tolerante).
- [x] **`MessagingConfig`** (`shared/`): `JacksonJsonMessageConverter` com o `JsonMapper` do Boot. Nenhuma fila declarada no código.
- [x] **`ExamDiscoveredListener`** em `core.exam_discovered`:
  - JSON quebrado, evento fora do contrato (Bean Validation), `event_type`/`version` desconhecidos ou dados inválidos → DLQ direto;
  - qualquer outro erro sobe → volta pra fila → DLQ após 3 entregas;
  - `event_id` e `correlation_id` no MDC (aparecem no log JSON do container).
- [x] **`ExamService.upsert`** (`@Transactional`): busca pela identidade → `create` ou `apply`. Devolve `CREATED`/`UPDATED`/`IGNORED`.
- [x] **Testes:** contrato (exemplo valida contra o schema com `format` como regra, e desserializa no DTO) e integração com Testcontainers usando a **topologia real** (`definitions.json` importado no RabbitMQ do teste): evento vira concurso, reentrega não duplica, evento atrasado não sobrescreve, versão desconhecida e JSON quebrado vão pra DLQ.
- [x] **Ponta a ponta:** `make scrape` → todos os eventos viram concursos, fila esvazia, DLQ vazia; rodar de novo não duplica.

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler: `ExamDiscoveredListener.handle` (o que vai pra DLQ e o que volta pra fila) e o comentário de concorrência em `ExamService.upsert`.
>
> ⚠️ **Achado no ponta a ponta:** o MCP do PCI passou a cortar em 30 concursos por chamada; o scraper trocou para o HTML do site (Fase 8 do GUIA do scraper). Identidade e contrato não mudaram.

---

## Fase 2b: catálogo de cargos ✅

O scraper agora publica o catálogo de cargos da fonte (ex.: "Enfermeiro", "Técnico em Contabilidade") e diz em quais cargos do catálogo cada nome cru apareceu.

- [x] **`V2__position_catalog.sql`**: tabela `position_catalog` (`UNIQUE (source, slug)`) e `exam_position.catalog_slugs TEXT[]` com índice GIN (`catalog_slugs @> ARRAY['enfermeiro']`).
  - **Array e não FK:** catálogo e concursos chegam por filas diferentes, sem ordem garantida entre elas; uma FK faria o concurso falhar se chegasse antes do catálogo.
- [x] **`CatalogPosition`** + **`CatalogPositionService.sync`**: upsert por `(source, slug)`, evento atrasado ignorado, cargo que some do catálogo fica.
- [x] **`PositionCatalogDiscoveredListener`** na fila `core.position_catalog_discovered` (infra), mesmas regras de DLQ do listener de concursos.
- [x] **`ExamPosition.catalogSlugs`** sincronizado no `Exam.apply` (o `catalog_slugs` é opcional no contrato: evento antigo vira lista vazia).
- [x] Testes: contrato do evento novo, serviço no Postgres real, listener com a topologia real.

> ✋ **Revisão:** implementada pelo Claude.

---

## Autenticação ✅

- [x] 🛠️ **Resource server JWT** (`shared/SecurityConfig`): tudo exige token do Keycloak, menos `/actuator/health/**`; sem sessão e sem CSRF (token no header); CORS só para a origem do web (`WEB_URL`).
  - `issuer-uri` = URL pública do Keycloak (é o `iss` do token); `jwk-set-uri` = rede interna (`keycloak:8080`); `audiences: core` (mapper `audience-core` no client `web` do realm).
- [x] **`GET /me`** (`user/MeController`): usuário do token. O `id` é o `sub` do Keycloak: use como chave do usuário nas tabelas do core (não o e-mail, que pode mudar).
- [x] Testes: sem token → 401, com token → dados do token, CORS só da origem do web. Ponta a ponta: token adulterado, de outro realm ou sem `aud` `core` → 401.

> Nos endpoints novos, pegue o usuário com `@AuthenticationPrincipal Jwt jwt` e `jwt.getSubject()`.

---

## Perfil do usuário ✅

Nome, e-mail e senha são do Keycloak (o web abre as telas dele com `kc_action=UPDATE_PROFILE` / `UPDATE_PASSWORD`). No core ficam só as preferências de estudo.

- [x] **`V3__user_profile.sql`**: `user_profile` (chave = `sub` do Keycloak, `ufs TEXT[]`) e `user_profile_position` (FK para `position_catalog`: só dá para seguir cargo que existe; índice para "quem segue o cargo X").
- [x] **`GET /catalog/positions`** (`exam`): catálogo em ordem alfabética do português.
- [x] **`GET` / `PUT /me/profile`** (`user`): o usuário vem do token, nunca do corpo. O `PUT` substitui tudo; cargo inexistente, UF inválida ou mais de 30 cargos → 400 (`BadRequestException` em `shared/`, formato ProblemDetail).
- [x] Testes: serviço no Postgres real e controller com token simulado.

> Próximo uso: filtrar os concursos da home e notificar quando abrir concurso para um cargo seguido (`user_profile_position` × `exam_position.catalog_slugs`).

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
