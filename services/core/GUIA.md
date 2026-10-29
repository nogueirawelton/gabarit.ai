# Guia do core

Roteiro do serviço, na ordem de implementação. Cada item diz **o que fazer**, **o que pesquisar** e **quando está pronto**. Marque o checkbox ao terminar e peça revisão ao fim de cada fase.

Itens com 🛠️ são infra: peça para o Claude fazer.

**Papel:** o `core` é todo o backend: API do web, concursos, planos de estudo, usuários, notificações. Fora dele ficam só os workers Python (`scraper`, `extraction`), ligados por fila.

**Primeiro fluxo:** `scraper` publica `exam.discovered` → fila `core.exam_discovered` → o `core` faz upsert do concurso e dos cargos → a API expõe para o web.

**Organização por assunto, não por camada:**
```
com.gabarit.core
├── exam/      tudo de concurso: entidade, repositório, serviço, listener, controller, DTOs
├── study/     carteira, simulados e plano de estudos (Fases 4–9)
├── document/  arquivos enviados e extrações da LLM (Fases 5–6)
├── user/      perfil, foto e "meus concursos" (usuário vem do token do Keycloak)
├── notification/  avisos de concurso novo (sininho) e resumo diário por e-mail
└── shared/    config, tratamento de erro, utilitários
```
Regra: um pacote usa o outro pelo serviço público dele (ex.: `ExamService`), nunca pelo repositório.

**Ids: UUID v7 em todas as tabelas** (`@Id @UuidGenerator(style = VERSION_7)`, coluna `UUID`). Id sequencial na URL deixa adivinhar recursos (`/estudos/5` → `/estudos/6`) e mostra o volume; o v7 é ordenado pelo tempo, então não espalha as inserções pelo índice como o UUID aleatório. Exceções com chave natural: `user_profile` (o `sub` do Keycloak) e `document_blob` (o SHA-256). A `V12__uuid_ids.sql` converteu as tabelas que nasceram com `BIGINT`, preservando dados e vínculos (conferido numa cópia do banco de dev). Tabela nova já nasce com `id UUID PRIMARY KEY`.

**Como rodar:**
- `make up`: stack inteira, `core` em `http://localhost:8080` (dev: `make restart s=core` recompila).
- **Ciclo rápido:** rode `CoreApplication` pela IDE no host com `CORE_DB_PASSWORD`, `RABBITMQ_USER` e `RABBITMQ_PASSWORD` no ambiente; os defaults do `application.yml` apontam para `localhost` (portas publicadas pelo docker-compose.yml de dev).
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
- [x] 🛠️ **MinIO** (`infra/minio`): bucket privado `avatars` e usuário `core` restrito a ele, criados pelo one-shot `minio-init`.
- [x] **Foto de perfil** (`V4__user_avatar.sql`, `AvatarService`, `POST`/`DELETE /me/avatar`; `avatarUrl` no `GET /me/profile`):
  - o web recorta e reduz no navegador; o core não confia no arquivo: formato pelos bytes (só JPEG/PNG), dimensões conferidas antes de decodificar, re-encode para JPEG 512px sem EXIF;
  - chave nova a cada upload (`{sub}/{uuid}.jpg`): grava o arquivo → troca a chave no banco (linha travada) → apaga o anterior;
  - leitura por URL pré-assinada com a URL **pública** do MinIO (a assinatura inclui o host), reaproveitada por 12h para o navegador cachear.
  - Testes: `AvatarImageTest`, controller, e `AvatarServiceTest` com MinIO real (mesmo `init.sh` da infra).

> Implementada pelo Claude. Pontos para ler: ordem das operações em `AvatarService.upload` (o que acontece se o banco ou o MinIO falhar) e `AvatarImage.read` (por que conferir o tamanho antes do `reader.read`).
>
> Próximo uso: filtrar os concursos da home e notificar quando abrir concurso para um cargo seguido (`user_profile_position` × `exam_position.catalog_slugs`).

---

## Fase 3: API de leitura ✅

- [x] **Contrato:** `GET /exams` (filtros `uf`, `status`, `q`, `page`, `size` até 50), `GET /exams/{id}` e `GET /me/exams` (`status`, `q`, `page`, `size`). Respostas em DTO (`ExamSummary`, `ExamDetail`, `PageResponse` em `shared/`), nunca entidade nem `Page` do Spring.
- [x] **`ExamStatus` calculado, nunca guardado:** `UPCOMING`/`OPEN`/`CLOSED`/`UNKNOWN`. Sem início conta como já começado: o PCI lista concurso aberto só com o fim (549 de 559 concursos têm fim, só 90 têm início). A regra existe em Java (`ExamStatus.of`) e em SQL (`ExamSpecifications.status`); um teste confere que as duas batem.
- [x] **"Hoje" no fuso de Brasília:** bean `Clock` em `shared/ClockConfig`; os testes fixam a data.
- [x] **Busca com `Specification`** (`ExamSpecifications`): um predicado por filtro presente. UF inclui os nacionais; `q` ignora caixa e trata `%`/`_` como texto; ordem fixa no servidor: abertos → em breve → sem data → encerrados, e dentro de cada grupo o fim mais próximo primeiro (sem fim por último).
- [x] **Detalhe:** `@EntityGraph` (uma query), só cargos ativos, em ordem alfabética do português; 404 com `NotFoundException` (ProblemDetail).
- [x] **"Meus concursos"** em `user/MyExamsController` (dependência user → exam): cargos seguidos agrupados por fonte, UFs do perfil; perfil sem cargo → lista vazia sem consultar. Cada item traz `matchingPositions` (os cargos seguidos naquele concurso), buscados numa query por página.
- [x] **`V5`: `catalog_slugs` virou `varchar[]`.** O Hibernate envia array de String como `varchar[]`, e o Postgres não tem `text[] && varchar[]`. Com a coluna em `varchar[]`, o filtro vira `catalog_slugs && ?` e usa o índice GIN (conferido com `EXPLAIN`). SQL à mão: `ARRAY['enfermeiro']::VARCHAR[]`.
- [x] **Testes:** `ExamStatusTest` (regras e fuso), `ExamSearchTest` (Postgres real: cada filtro, combinados, bordas de data, ordem, paginação e **número fixo de queries**: 3 com página de 5 ou de 30), `ExamControllerTest` e `MyExamsControllerTest`.
- [x] **Web:** home com "Para você" / "Todos", busca (com debounce, na URL) e "Carregar mais"; página `/concursos/:id` com datas, vagas, cargos e link para a fonte.

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler:
> - `ExamSpecifications.matching`: por que a ordem fica no servidor e por que o `orderBy` dentro da Specification não quebra a query de contagem;
> - `ExamSearchTest.numeroDeQueriesNaoDependeDoTamanhoDaPagina`: como medir N+1 com as estatísticas do Hibernate;
> - `ExamFilter.catalogSlugs`: diferença entre `null` (sem filtro) e mapa vazio (nada).
>

### Escolaridade e busca sem acento ✅

- [x] **`V6`: escolaridade como níveis.** `EducationLevel` (`FUNDAMENTAL`, `MEDIO`, `TECNICO`, `SUPERIOR`) extraído do texto livre da fonte (`EducationLevel.parse`, a cada evento) para `exam.education_levels`; os concursos que já estavam no banco foram preenchidos pela própria migration, com a mesma regra em SQL. O texto original continua para exibição.
- [x] **Perfil:** `user_profile.education_levels`; o `PUT /me/profile` passa a exigir `educationLevels` (nível desconhecido → 400). Vazio = qualquer nível, como as UFs.
- [x] **Filtro:** `GET /me/exams` usa os níveis do perfil, e `GET /exams` aceita `educationLevel`. Basta um nível em comum; concurso sem escolaridade informada entra sempre. O filtro é no nível do concurso: um concurso "Médio / Superior" aparece para quem marcou Superior mesmo que o cargo seguido seja de nível médio (a fonte não diz a escolaridade de cada cargo).
- [x] **`V7`: busca sem acento** com a extensão `unaccent`. Ela é *trusted* (PG 13+): o dono do banco cria pela migration, sem superusuário e sem mexer na infra. O título passa por `unaccent(lower(...))` no SQL; o texto digitado é normalizado em Java. Sem índice por enquanto (centenas de concursos); quando pesar, índice trigram numa função `IMMUTABLE` que embrulhe o `unaccent`.
- [x] Testes: `EducationLevelTest`, filtro de escolaridade e busca sem acento no `ExamSearchTest`, perfil e controllers.

---

## Notificações ✅

Concurso novo que bate com o perfil vira aviso no sininho do app e entra no resumo diário por e-mail. Decisões de produto: e-mail + sininho, **resumo diário** (não um e-mail por concurso), só concurso **novo** (atualização, reabertura e prorrogação não avisam).

- [x] **Gatilho:** o `ExamService.upsert` publica `ExamCreatedEvent` (evento do Spring, não do RabbitMQ) só quando cria o concurso. O `NotificationService` ouve com `@EventListener`, **na mesma transação**: se gravar o aviso falhar, o concurso também não é gravado e a mensagem volta para a fila. Assim cada interessado recebe o aviso uma vez. Reentrega e atualização não avisam (viram `IGNORED`/`UPDATED`); concurso que já chega encerrado também não.
- [x] **Quem é avisado:** `UserProfileRepository.findInterested`, as mesmas regras do `GET /me/exams` vistas do lado do usuário (cargo seguido da mesma fonte, UF ou nacional, nível de escolaridade em comum). Um teste confere que os avisados são exatamente os que veriam o concurso em "meus concursos".
- [x] **Sininho:** `GET /me/notifications`, `GET /me/notifications/unread-count`, `POST /me/notifications/{id}/read`, `POST /me/notifications/read-all`. Aviso de outro usuário → 404.
- [x] **Resumo diário** (`NotificationDigestJob`, `@Scheduled` às 8h de Brasília, `NOTIFICATION_DIGEST_CRON`): um e-mail por usuário com os avisos ainda não enviados dos últimos 7 dias; cada usuário numa transação, com os avisos travados (`FOR UPDATE SKIP LOCKED`: duas instâncias do core não mandam em dobro) e marcados como enviados só se o envio deu certo. Concurso que encerrou desde o aviso sai do e-mail. Avisos com mais de 90 dias são apagados.
- [x] **Contato:** o e-mail é do Keycloak; o core guarda a cópia do token (`email`, `email_verified`, `given_name`) quando o usuário abre o app (`GET /me/profile`). Só e-mail confirmado recebe. O usuário desliga o resumo no perfil (`PUT /me/profile/email-digest`); o sininho continua.
- [x] 🛠️ **SMTP:** o core usa o mesmo SMTP do Keycloak em produção; em dev, o **Mailpit** (`http://localhost:8025`), para nunca mandar e-mail de verdade testando. SMTP fora do ar não derruba o health do core.
- [x] **Testes:** `NotificationTest` (quem é avisado, reentrega, paridade com "meus concursos", sininho) e `NotificationDigestJobTest` com SMTP de verdade no teste (GreenMail): quem recebe, conteúdo, não reenviar, janela de 7 dias, concurso encerrado.

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler:
> - `NotificationService.onExamCreated`: por que na mesma transação (e o custo: um bug aqui trava a ingestão de concursos, que vai para a DLQ depois de 3 tentativas);
> - `NotificationDigestJob.sendTo`: a ordem travar → enviar → marcar, e o que acontece se o commit falhar depois do envio (o usuário recebe em dobro no dia seguinte: raro e aceitável);
> - `UserProfileRepository.findInterested`: os `CAST` explícitos (parâmetro nulo e array sem tipo confundem o Postgres).
>
> Pendente: link de descadastro em um clique no e-mail (hoje o link leva ao perfil, que exige login).

---

# Estudo: carteira, documentos, simulado e plano

**Objetivo:** a pessoa estuda para um concurso, aberto ou passado, sem esperar o próximo edital. Ela põe o concurso na **carteira**, envia o **edital** e as **provas relacionadas com gabarito** (do mesmo cargo ou banca, deste concurso ou de outros), e o app usa a LLM (Claude, no worker `extraction`) para montar o conteúdo programático, os **simulados** e o **plano de estudos**.

**Decisões já tomadas:**
- **Os PDFs vêm do usuário, por upload.** O projeto não baixa PDF de terceiros: o PCI bloqueia por `robots.txt` e captcha, e não contornamos isso (ver o GUIA do scraper).
- **Concurso passado entra pela carteira, não pelo scraper.** O edital é a fonte da verdade: dele saem órgão, banca, ano, cargos, data da prova e conteúdo. Coletar encerrados da `/vagas/` fica como opção futura.
- **LLM = Claude, chamada só pelo worker Python `extraction`.** O `core` orquestra (pede por fila, guarda o resultado e avisa no sininho); nenhum segredo da Anthropic fica no `core`.
- **Mesmo arquivo, uma extração.** Arquivos são identificados pelo hash (SHA-256) do conteúdo; o resultado de uma tarefa é reaproveitado entre usuários. O arquivo e o acesso continuam privados de cada um: ninguém vê o que o outro enviou, só não pagamos duas vezes pela mesma leitura.

**Pacotes novos:**
```
com.gabarit.core
├── study/     carteira (alvos de estudo), simulados e plano de estudos
└── document/  arquivos enviados, pedidos de extração e resultados (cache por hash)
```
Dependências: `study → document → (nada de domínio)`; `study → exam` (concurso da lista). O `document` não conhece carteira: guarda arquivos e resultados, e quem dá sentido a eles é o `study`.

**Ordem das fases e por quê:** carteira e upload (4 e 5) não usam LLM e destravam o resto; o pipeline (6) é a fundação de todas as tarefas; edital (7) é a tarefa mais simples de validar e é pré-requisito de questão e plano; simulado (8) dá o diagnóstico que o plano (9) usa; custo (10) entra quando houver uso real para medir.

---

## Fase 4: carteira (pacote `study`) ✅

- [x] **`V9__study_target.sql`**: `study_target` = um concurso que a pessoa estuda. `exam_id` liga ao concurso da lista (nulo = cadastrado à mão; `ON DELETE SET NULL`: concurso apagado não apaga o estudo); `title`, `organ`, `board`, `year` (1950–2100), `position_name`, `exam_date`. Único `(user_id, exam_id)`: o mesmo concurso da lista entra uma vez.
- [x] **API** (`StudyTargetController`): `GET /me/study-targets`, `POST` (`{examId}` ou `{title}`, exatamente um; 201 com `Location`, ou 200 se o concurso já estava), `GET`/`PUT`/`DELETE /me/study-targets/{id}`. Item de outro usuário → 404. Limite de 20 por carteira.
- [x] **Concurso da lista** entra com título, órgão (título sem o " - UF") e ano (das datas de inscrição); o cargo tem de ser um dos cargos ativos do concurso. **Cargo do perfil:** se um só cargo do concurso está entre os que a pessoa segue, ele já vem escolhido; com mais de um, nada é escolhido por ela, mas `suggestedPositions` traz esses cargos e o web os mostra primeiro ("Seus cargos"). A regra é a mesma do "meus concursos" (`ProfileView.catalogSlugsBySource` + `ExamService.matchingPositions`). **Concurso à mão** aceita qualquer cargo.
- [x] **Web:** "Estudos" na navegação (cabeçalho e dock), página da carteira com "Cadastrar concurso" (só o título), página do estudo com os dados editáveis (cargo em lista quando o concurso é da lista) e "Remover dos estudos"; botão "Estudar para este concurso" na página do concurso. O bloco de arquivos aparece como "em breve" (Fase 5).
- [x] Testes: `StudyTargetServiceTest` (Postgres real: preenchimento, não duplicar, cargo do concurso, carteira de outro usuário, limite) e `StudyTargetControllerTest`.

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler: `StudyTargetService.addExam` (idempotência e o caso dos dois cliques simultâneos, comentado) e por que o `PUT` manda todos os campos (vazio apaga).

---

## Fase 5: documentos (pacotes `document` e `study`) ✅

Divisão: o **`document`** cuida só de arquivo (URL de envio, conferência, um blob por conteúdo) e não conhece carteira; o **`study`** liga o arquivo ao concurso com o tipo e o status. Assim a dependência é `study → document`, sem ciclo, e o `document` serve depois para outros usos.

- [x] 🛠️ **MinIO:** bucket privado `documents` (o `core` lê e escreve); regra de ciclo de vida que apaga `uploads/` em 1 dia (envio abandonado); CORS do `PUT` só para a origem do web (conferido com o preflight do navegador). O usuário `extraction` (só leitura) entra com o worker (Fase 0 do GUIA do extraction).
- [x] **`V10__document.sql`**: `document_blob` (PK = SHA-256; `storage_key`, tamanho, páginas) e `study_document` (dono, concurso com `ON DELETE CASCADE`, `kind`, nome original, `status` `UPLOADING`/`READY`/`REJECTED` com o motivo, e o blob).
- [x] **`V11__study_paper.sql`: prova relacionada com dois espaços.** `study_paper` (concurso, ano, tipo/cor, `answers_included`) e `study_document.paper_id`. Edital é do concurso (`paper_id` nulo, quantos quiser: retificações); prova e gabarito ocupam os espaços de uma prova relacionada, **um de cada** (índice único `(paper_id, kind)`). Assim o par prova ↔ gabarito é explícito desde o envio, e a Fase 8 não precisa adivinhar. "Gabarito no mesmo arquivo" é uma opção da prova (sem espaço de gabarito). A migration converte arquivos avulsos que já existissem (cada um vira uma prova relacionada).
- [x] **Envio em três passos** (`StudyDocumentController`):
  1. `POST /me/study-targets/{id}/documents` (`kind`, `paperId` para prova/gabarito, `name`, `size`) → URL pré-assinada de `PUT` para `uploads/<usuário>/<id>.pdf`, válida por 15 min, com o `Content-Type: application/pdf` **na assinatura** (outro tipo → 403 do MinIO);
  2. o navegador envia direto ao MinIO (com barra de progresso);
  3. `POST .../documents/{docId}/complete` → `DocumentService.ingest`: baixa em stream para arquivo temporário calculando o SHA-256, confere o cabeçalho `%PDF-`, abre com o PDFBox (cache em disco) para contar páginas, e recusa o que não é PDF, está corrompido, tem senha, passa de 30 MB ou de 600 páginas (limites da API do Claude). Aprovado → copiado para `blobs/<sha256>.pdf` (dentro do MinIO), um blob por conteúdo. Recusado volta **200** com `REJECTED` e o motivo (fica na lista); confirmar de novo devolve o mesmo resultado.
- [x] **Falha de rede no meio da conferência não perde o envio:** o arquivo só é apagado de `uploads/` quando foi aprovado ou recusado; erro de MinIO/disco deixa o envio para tentar de novo.
- [x] **`V13__paper_edital.sql`: edital daquela prova** como terceiro espaço (opcional) da prova relacionada. Edital **sem** prova relacionada é o do concurso-alvo; **dentro** de uma prova, é o do ano dela. Serve para classificar as questões de 2019 pelo conteúdo do edital de 2019 (não pelo atual) e para comparar o que entrou e saiu entre editais. O mesmo edital em várias provas (vários cargos e tipos no mesmo ano) é um arquivo só, e uma leitura só da LLM.
- [x] **Provas relacionadas:** `POST /me/study-targets/{id}/papers` (ano, tipo/cor, gabarito junto), `DELETE .../papers/{paperId}` (leva os arquivos dela); `GET .../materials` devolve editais e provas com os dois espaços. Espaço com arquivo aprovado não é sobrescrito (remover é explícito); envio recusado ou não concluído é substituído pelo novo.
- [x] **Limites:** 30 arquivos e 12 provas relacionadas por concurso, 50 envios por usuário em 24 h. Envio começado e nunca confirmado some em 1 dia (job de madrugada).
- [x] **Abrir o arquivo:** cada arquivo aprovado vem com uma URL pré-assinada de leitura (1 h).
- [x] **Web:** seção "Edital e provas relacionadas" na página do estudo: lista de editais e um cartão por prova relacionada ("Prova de 2019 · Tipo 1") com os espaços **Prova** e **Gabarito** lado a lado; barra de progresso, motivo da recusa no próprio espaço, remover.
- [x] Testes: `DocumentServiceTest` (MinIO e Postgres reais, envio como o navegador: PDF válido, mesmo arquivo duas vezes = um blob, não-PDF, corrompido, com senha, não chegou, Content-Type errado → 403), `StudyDocumentServiceTest` (ponta a ponta com os dois espaços, espaço ocupado, recusado substituído, gabarito junto, edital × prova, outro usuário, limites, abandono, cascatas) e `StudyDocumentControllerTest`.

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler: `DocumentService.ingest` (o que apaga e quando) e por que o `complete` recusado responde 200.
>
> Pendente, anotado: blob sem nenhum `study_document` apontando continua no MinIO (pode ser de outra pessoa; limpar com um job quando pesar).

---

## Fase 6: pipeline de extração (core ↔ `extraction`) ✅

- [x] 🛠️ **Contratos** em `contracts/`: comando `extraction.requested` (`extraction_id`, `task`, `prompt_version`, `documents` = hash + chave no bucket, `params`) e eventos `extraction.completed` (`result` validado pelo schema da tarefa, `model`, `usage` com tokens) / `extraction.failed` (`reason`, `retryable`).
- [x] 🛠️ **Filas** no `definitions.json`: `extraction.requests` (consumida pelo worker) e `core.extraction_results` (recebe os dois eventos), ambas com DLQ.
- [x] **`V14__extraction.sql`**: `extraction` = cache de resultados. Chave única `(task, inputs_hash, prompt_version)`; `status` (`PENDING`, `DONE`, `FAILED`), `inputs`/`result` JSONB, `retryable`, `model` e tokens. Custo em dólar: ainda não calculado (tokens e modelo gravados; a tabela de preços tem faixas por tamanho de prompt em alguns modelos, ex.: Haiku 5.5 acima de 100 mil tokens).
- [x] **`ExtractionService.request(task, documentos, params)`** (pacote `extraction`): hash do JSON canônico das entradas (chaves em ordem, listas na ordem). Existe com a mesma chave → devolve (**cache entre usuários**; `PENDING` só aguarda); `FAILED` com `retryable` → pede de novo; senão grava `PENDING` e publica depois do commit. Envio falhou → `FAILED` retryable.
- [x] **Listener** (`ExtractionResultListener`): grava o resultado (repetido ou atrasado é ignorado) e publica `ExtractionFinishedEvent` (evento interno do Spring). Quem reage (o `study`, aviso no sininho) entra na Fase 7.
- [x] **Versão do prompt na chave:** `ExtractionTask` guarda a versão que o core pede; prompt novo no worker → versão nova aqui → a próxima leitura reprocessa.
- **Pronto:** `ExtractionRoundTripTest` (Testcontainers, o teste faz o papel do worker) e ida e volta real com o worker no compose (`ECHO` → `DONE`; hash que não confere → `FAILED`).
- **Fica para depois:** pedido `PENDING` que nunca volta (worker parado por dias, mensagem na DLQ) não tem prazo. Se aparecer, um job que reenvia `PENDING` antigos resolve.

---

## Fase 7: edital → concurso, cargos e conteúdo

- [x] **Tarefa `EDITAL_OVERVIEW`** (edital inteiro): órgão, banca, ano, inscrições, data da prova, cargos (nome, especialidade, escolaridade, vagas, salário).
  - **Pedida pela pessoa** (`POST .../documents/{id}/reading`, botão "Ler o edital"), não no envio: arquivo errado não gasta leitura. O mesmo endpoint pede de novo depois de falha passageira; edital já lido (o mesmo PDF de outra pessoa) volta pronto, sem custo. `V15__edital_reading.sql`: `study_document.extraction_id`.
  - `ExtractionService` grava o pedido em transação própria (`REQUIRES_NEW`): chamado dentro de outra, o resultado poderia chegar antes do commit.
  - A leitura vai junto com o arquivo nos materiais (`reading`: status, `overview`, motivo da falha). **Nada é aplicado sozinho:** no web, "Usar no concurso" (órgão, banca, ano, data; o que o edital não traz não apaga o que já existe) e "Escolher" no cargo. Enquanto lê, o web confere a cada 5 s.
  - **Cargo do edital** (`V17__edital_position.sql`: `study_document.position_name`/`position_specialty`; `PUT .../documents/{id}/position`): o cargo escolhido entre os da leitura, como o edital escreve, guardado **no arquivo do edital**, separado do cargo do concurso. Num concurso da lista, o cargo do concurso continua o coletado pelo scraper (os nomes não batem com os do edital: "ANALISTA DE SISTEMAS" × "Analista – Sistemas"); no concurso à mão, a escolha vira também o cargo do concurso. O core só aceita cargo que está na leitura (nome e especialidade); outra leitura do edital (prompt novo) desfaz a escolha, e remover o edital a leva junto. É a entrada do `EDITAL_SYLLABUS`.
  - **Aviso no sininho** quando a leitura termina (`V16__notification_type.sql`: `notification.type` = `NEW_EXAM` / `EDITAL_READ` / `EDITAL_FAILED`, com o concurso da carteira e o título). O `study` publica `EditalReadEvent` (um por concurso que tem aquele edital: a leitura é por conteúdo e avisa todos) e o `notification` grava; `study` não depende de `notification`. Aviso de edital fica só no sininho: o resumo por e-mail continua só com concursos novos.
- [x] **Tarefa `EDITAL_SYLLABUS` v1** (edital + cargo do edital, acima): pedida pela pessoa depois de escolher o cargo (`POST .../documents/{id}/syllabus`, botão "Ler o conteúdo"; escolher só grava: cargo errado não gasta leitura), com `params.position` = nome e especialidade como a visão geral leu; a leitura é por edital + cargo, compartilhada entre quem escolheu o mesmo. `V18__edital_syllabus.sql`: `study_document.syllabus_extraction_id`; trocar de cargo ou outra leitura do edital desfaz o vínculo. Volta nos materiais (`syllabus`: status, disciplinas com bloco, tópicos e página). No web, abaixo da lista de cargos, com as disciplinas abrindo uma por vez; o mesmo endpoint pede de novo depois de falha passageira.
  - **Quadro de provas** (`EDITAL_OVERVIEW` v2, que já lê o corpo): provas distintas (a quem valem, total de questões, múltipla escolha ou certo/errado, discursiva) com questões e peso por disciplina, e a prova de cada cargo (`examId`). No web, "Como é a prova" no passo Cargo e as questões ao lado de cada disciplina do conteúdo (casadas pelo nome).
  - **Leitura desatualizada:** `Reading`/`SyllabusReading` trazem `outdated` (prompt da leitura ≠ o que o core pede hoje); o web mostra "Ler de novo", e o mesmo endpoint lê com o prompt atual. Ler o edital de novo **mantém** o cargo escolhido e o conteúdo dele (o conteúdo é pedido pelo nome do cargo); se o cargo some da leitura nova, o web avisa.
  - **Falta:** aviso no sininho quando o conteúdo fica pronto; cópia para tabelas: feita (V21, na ligação questão → tópico).
- [ ] **Editais das provas relacionadas** (espaço "edital desta prova"): o mesmo `EDITAL_SYLLABUS` para cada um, e um **casamento de tópicos entre anos** (o mesmo tópico com nomes um pouco diferentes vira um só), para mostrar o que é recorrente, o que entrou e o que saiu.
- [x] **`V21__question_topics.sql`**: `syllabus_subject`/`syllabus_topic` (conteúdo do cargo copiado da leitura `EDITAL_SYLLABUS` na primeira ligação; compartilhado), `question_topic` (questão → tópico) e `topic_classification` (o pedido de cada prova relacionada: questões de qual leitura, tópicos de qual conteúdo).
- [ ] **Web:** "Conteúdo" do alvo: disciplinas e tópicos, com o que a pessoa já estudou marcado.
- **Pronto quando:** com 10 editais reais (de bancas diferentes), órgão, banca, data e cargos batem, e o conteúdo de um cargo bate com o PDF numa conferência manual.

---

## Fase 8: prova + gabarito → questões, e o simulado

- [x] **Tarefa `EXAM_PAPER` v1 (leitura das questões)**: prova + gabarito (ou só a prova, quando o gabarito vem nela) + `params.variant` (tipo/cor, escolhe a coluna do gabarito) → questões (número, disciplina como a prova agrupa, tipo A–E/certo-errado/discursiva, enunciado, alternativas, resposta, anulada, imagem, página) e textos-base compartilhados. **Pedida pela pessoa** (`POST .../papers/{id}/reading`, botão "Ler as questões", só com prova e gabarito enviados). `V19__paper_reading.sql`: `study_paper.extraction_id`; trocar o PDF da prova ou do gabarito desfaz o vínculo. O web mostra o resumo (quantas, anuladas, por disciplina) e avisa gabarito não achado, questões sem resposta e tipo diferente do informado. A leitura é compartilhada (mesma prova, gabarito e tipo).
  - **Questão → tópico** (pacote `simulation`, `TopicService`): tarefa `QUESTION_TOPICS` (sem documento: as questões já lidas e os tópicos do conteúdo, com código "disciplina.tópico", vão nos parâmetros) → o tópico de cada questão. Pedida pela pessoa em cada prova (`POST .../papers/{id}/topics`, botão "Ligar aos tópicos"); o listener do `ExtractionFinishedEvent` grava as ligações (troca, não soma, numa leitura nova). `GET .../topics` devolve o andamento de cada prova (desatualizada se mudou o prompt, a leitura da prova ou o conteúdo) e quantas questões caem em cada tópico; no web, "caiu N×" em cada tópico e o total por disciplina no passo Cargo.
  - **Desempenho por tópico no simulado:** depois de entregar, cada questão mostra o tópico (do conteúdo atual do cargo) e o resultado traz `byTopic` (onde mais errou primeiro); no web, "Onde você mais errou". Durante a prova o tópico não vai (seria pista).
  - **Questões no estilo da banca** (`GenerationService`): tarefa `TOPIC_QUESTIONS` (sem documento; params só de conteúdo: banca do concurso, formato da prova do cargo, número de alternativas mais comum nas provas relacionadas, disciplina e tópico → 5 questões com gabarito e comentário). Compartilhada: mesma banca e tópico, a mesma leitura. `V24__generated_questions.sql`: `question.generated`/`explanation` e `topic_generation` (o pedido de cada tópico no concurso). As geradas entram em `question` ligadas ao tópico e na prática (do tópico e das sessões do plano, depois das reais); não contam no "caiu N×". No web: "Gerar questões" e "Praticar (N)" em cada tópico do passo Cargo; no simulado, selo "gerada por IA" e o comentário depois de entregar.
  - **Falta:** prova longa demais para uma resposta (dividir por páginas); avaliar a qualidade das geradas (erro de conteúdo é o risco; "Questão com problema" ajuda).
- [x] **`V20__simulation.sql`**: `question` (questões de uma leitura `EXAM_PAPER`, copiadas do resultado no primeiro simulado daquela prova; compartilhadas, ligadas à leitura e não ao usuário; texto-base copiado em cada questão), `simulation` (concurso, prova, título, início, prazo opcional, entrega) e `simulation_answer` (uma por questão). O tópico de cada questão entra com a classificação.
- [x] **Simulado** (pacote `simulation`, depende do `study` só por `StudyDocumentService.paperQuestions`): começar com uma prova lida e tempo opcional (`POST .../simulations`), marcar e desmarcar a cada clique (`PUT /me/simulations/{id}/answers/{q}`, só alternativa da questão ou C/E), entregar (`POST .../finish`). **Correção no servidor:** enquanto corre, o web não recebe gabarito nem anuladas. Tempo esgotado (com 2 min de folga) encerra sozinho, valendo o prazo. Nota: anulada conta como certa; sem resposta no gabarito fica fora; prova toda de certo/errado mostra a nota líquida (certas − erradas). Discursivas ficam fora do simulado.
- [ ] **"Questão com problema"**: a pessoa reporta (enunciado cortado, gabarito errado); reportes viram dado para melhorar o prompt.
- [x] **Web:** "Começar simulado" no passo Estudar (prova lida + tempo), histórico dos simulados do concurso; `/simulados/:id` com uma questão por vez, texto-base recolhível, cronômetro (regressivo com prazo), mapa de questões, "revisar depois" (só no navegador) e entrega com aviso de questões em branco; resultado com nota, por disciplina e correção questão a questão (todas, erradas, em branco).
- **Pronto quando:** dá para fazer uma prova relacionada inteira e a nota bate com a correção manual pelo gabarito.

---

## Fase 9: plano de estudos

- [x] **Decisão:** sem LLM. O plano é uma conta no core (`plan/PlanBuilder`, pura e testada): barata, replaneja de graça e explica o porquê. A LLM fica para o que agrega (resumo por tópico, depois).
- [x] **Entradas:** conteúdo do cargo (`StudyDocumentService.targetSyllabus`), quadro de provas do cargo (`targetExam`: questões × peso, casado pelo nome da disciplina), o que cai nas provas relacionadas (`TopicService.overview`), quanto erra nos simulados (`SimulationService.topicPerformance`), data da prova e minutos por dia da semana (a pessoa informa).
- [x] **Conta:** tempo da disciplina ∝ peso × (1 + quanto cai, 0–1) × (1 + quanto erra, 0–1); tópicos na ordem do edital, com mais tempo para o que cai e o que erra; blocos de até 1 h alternando disciplinas; 30 min de revisão no último dia de estudo de cada semana; fase final só de revisão (última semana, ou 1/5 dos dias) com um simulado completo perto de 3 dias antes da prova.
- [x] **`V22__study_plan.sql`** (`study_plan`: um por concurso, data da prova e minutos por dia; `plan_session`: data, ordem, tipo estudo/revisão/simulado, disciplina, tópicos, minutos, feito). API: `GET/PUT /me/study-targets/{id}/plan` (PUT monta ou refaz: as sessões feitas ficam e os tópicos delas contam como estudados), `PUT /me/plan-sessions/{id}` (feito), `GET /me/plan-sessions?from&to` (todos os concursos, para a agenda).
- [x] **Web:** "Montar plano" no passo Estudar (data da prova e horas de cada dia da semana); painel com progresso, tempo por disciplina, a semana atual e as próximas (com o "feito"), aviso de sessões atrasadas com "Refazer plano"; em Meus concursos, "Estudar hoje" e os dias de estudo marcados na agenda.
- [x] **Praticar em cada sessão:** a sessão mostra quantas questões das provas relacionadas caem nos tópicos dela (objetivas, não anuladas, com gabarito; `TopicService.practiceQuestions`); "Praticar" cria um simulado de prática sem tempo com até 20 delas (`POST /me/plan-sessions/{id}/practice`). `V23__practice.sql`: simulado sem prova de origem (`extraction_id` nulo) com a lista em `simulation_question`; correção e "onde mais errou" iguais aos do simulado.
- [ ] **Concurso aberto chega:** quando sai edital novo para o mesmo cargo, oferecer migrar o plano para o conteúdo novo.
- **Pronto (parcial):** a pessoa recebe um plano até a data da prova, marca sessões como feitas e refaz quando atrasa ou depois de um simulado. Falta replanejar sozinho e as questões por sessão.

---

## Fase 10: custo e limites

- [ ] Painel simples (consulta SQL) de custo por tarefa, por usuário e por mês, com o `usage` que o worker devolve.
- [ ] Limites por plano de usuário (gratuito/pago) aplicados no `ExtractionService.request` (antes de publicar).
- [ ] Reprocessamento em lote (prompt novo) pela **Batches API** (metade do preço, sem pressa).
- **Pronto quando:** dá para responder "quanto custa um usuário ativo por mês".

---

## Depois

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
