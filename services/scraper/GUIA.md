# Guia do scraper

Roteiro do serviço, na ordem de implementação. Cada item diz **o que o arquivo faz**, **o que pesquisar** e **quando está pronto**. Marque o checkbox ao terminar e peça revisão ao fim de cada fase.

Itens com 🛠️ são infra: peça para o Claude fazer.

**Fluxo do serviço:** recebe `scrape.requested` da fila → coleta o catálogo de cargos e **todos** os concursos abertos da fonte → publica um `position_catalog.discovered` e um `exam.discovered` por concurso → o serviço `core` persiste.

**Concurso e cargos:** um `Exam` tem vários `Position` (1:N). No scraper, o cargo só existe dentro do concurso: não tem ciclo de vida próprio e é publicado junto com ele.

**Decisões que moldaram o desenho:**
- **Fonte principal: páginas públicas do PCI Concursos (HTML)**, em duas etapas: catálogo de cargos (`/cargos/` + `/vagas/`) e, para cada cargo, `/cargos/<slug>` (id do PCI + nomes crus) e `/vagas/<slug>` (UF, inscrições, vagas). Até 2026-10 era o MCP público do PCI, que passou a cortar em 30 concursos por chamada (ver Fase 8).
- **Sem PDF no scraper.** Os editais e provas do PCI ficam atrás de captcha (Cloudflare Turnstile), e o projeto **não contorna** essa proteção. O scraper guarda só a URL; os arquivos entram depois, por upload ou pela fonte oficial da banca. Por isso não há MinIO nem `FileStorage` aqui.
- **Identidade:** concurso = `(source, external_id)`; cargo = nome cru dentro do concurso. Nada de ID gerado no scraper: a mesma coleta tem que produzir a mesma identidade.

---

## Fase 0: preparação ✅

- [x] `src/main.py` e `__init__.py` em `src/` e em cada subpasta (`python -m src.main` roda a partir de `services/scraper/`).
- [x] Ambiente local: `.venv` com Python 3.13 (via `uv`) e `requirements.txt` + `requirements-dev.txt`. Serve para o editor e testes rápidos; o oficial é o container.
- [x] 🛠️ `pyproject.toml` (pytest e ruff) e `pyrightconfig.json` na raiz (cada serviço é raiz de import).
- [x] 🛠️ Dependências: `pydantic-settings`, `mcp==2.3.0`, `playwright==1.63.0`; `ruff` e `jsonschema` no dev.

---

## Fase 1: domínio ✅ (sem IO, sem libs de infra)

Regra da fase: nada aqui importa `httpx`, `aio_pika`, `mcp` ou qualquer coisa de `infrastructure`.

- [x] **`src/domain/entities/position.py`**: `Position(name)`, imutável, nome como a fonte escreve.
- [x] **`src/domain/entities/exam.py`**: `Exam` imutável com `source`, `external_id`, `title`, `url`, `uf` (None em nacionais), `region`, `education_level`, `openings` (texto cru, ex.: "454 vagas até R$ 8.628,87"), datas de inscrição, `registration_note` ("Suspenso", "Prorrogado"...) e `positions`.
  - Valida: cargo repetido no mesmo concurso e fim de inscrição antes do início.
  - **Pesquisar:** *pydantic frozen models*, *model_validator mode="after"*, *tuple vs list em entidade imutável*.
- [x] **`src/domain/errors.py`**: `ScraperError` e três filhas, separando o que vale tentar de novo do que não vale:
  - `UnknownSourceError` (permanente), `SourceUnavailableError` (transitório), `InvalidSourceDataError` (a fonte mudou de formato).
- [x] **`src/domain/ports/exam_source.py`** e **`event_publisher.py`**: `Protocol`s; nenhum menciona RabbitMQ, MCP ou HTTP.
  - **Pesquisar:** *typing.Protocol*, *structural subtyping*.

---

## Fase 2: aplicação ✅ (caso de uso + contratos)

- [x] **`contracts/commands/scrape.requested.v1.json`** e **`contracts/events/exam.discovered.v1.json`** (na **raiz do repo**): fonte da verdade compartilhada com o `core` (Java). O evento leva o concurso **inteiro, com os cargos dentro**, para o `core` fazer upsert por `(source, external_id)`.
  - **Pesquisar:** *JSON Schema draft 2020-12*, *envelope de evento*, *event-carried state transfer*.
- [x] **`src/application/messages.py`**: `ScrapeRequested` (recusa campo desconhecido) e `ExamDiscovered` (envelope com `event_id`, `event_type`, `version`, `occurred_at` em UTC, `correlation_id` e `data`).
- [x] **`src/application/use_cases/scrape_exam.py`**: `ScrapeExams` busca tudo **antes** de publicar (se a fonte falha, nada sai) e publica um evento por concurso.
- [x] **`tests/fakes.py`** e **`tests/unit/test_scrape_exam.py`**: evento com os dados certos, fonte desconhecida e fonte fora do ar não publicam nada, coletar de novo mantém a identidade.
- [x] **`tests/unit/test_contracts.py`**: os eventos gerados batem com o JSON Schema (no container de dev, `contracts/` é montado em `/contracts`).

> ✋ **Revisão:** leia as fases 1 e 2 e pergunte o que não estiver claro; foram implementadas pelo Claude.

---

## Fase 3: fonte PCI via MCP ✅ (substituída pela Fase 8)

- [x] **`tests/fixtures/pci/*.json`**: respostas reais recortadas (concurso nacional, suspenso sem datas, concurso que só aparece com `professores=true`).
- [x] **`src/infrastructure/sources/pci_mcp.py`**:
  - `parse_listing` / `merge_listings`: funções puras, testáveis só com o JSON. Concurso malformado é ignorado com log, sem derrubar a coleta; resposta fora do formato vira `InvalidSourceDataError`.
  - `PciMcpSource`: abre uma sessão MCP, faz as duas chamadas e faz o parse **fora** da sessão (erro de formato não vira erro de conexão). Rede, timeout e erro do MCP viram `SourceUnavailableError`.
  - **Pesquisar:** *MCP streamable HTTP client*, *anyio TaskGroup e ExceptionGroup*, *asyncio.timeout*.
- [x] **Registro de fontes**: `build_sources` em `src/infrastructure/container.py`. Fonte nova = uma entrada nova.
- [x] **`tests/unit/test_pci_parser.py`**.

---

## Fase 4: configuração e bootstrap ✅

- [x] **`src/config.py`**: único lugar que lê o ambiente (`pydantic-settings`, senha em `SecretStr`); variável obrigatória faltando quebra no boot.
- [x] **`src/infrastructure/logging.py`**: JSON no stdout, com `message_id` via `contextvars`. Campos passados em `extra=` entram no JSON.
- [x] **`src/infrastructure/container.py`**: composition root; único lugar que conhece as classes concretas.
- [x] **`src/main.py`**: só bootstrap, trata SIGTERM/SIGINT. `docker stop` termina em menos de 1s.

---

## Fase 5: mensageria ✅

- [x] 🛠️ RabbitMQ 3.13 no compose, com healthcheck, volume, `depends_on` e `.env.example`.
- [x] **`src/infrastructure/messaging/rabbitmq_publisher.py`**: exchange topic `gabarit.events`, routing key = `event_type`, mensagem persistente, `message_id` = `event_id`, **publisher confirms**.
- [x] **`src/infrastructure/messaging/rabbitmq_consumer.py`**:
  - topologia: `gabarit.commands` → fila quorum `scraper.scrape_requested` → (dead letter) `gabarit.dlx` → `scraper.scrape_requested.dlq`;
  - malformada / fonte desconhecida / formato mudou → DLQ direto; transitório → volta pra fila, e após 3 reentregas (`x-delivery-limit`) vai pra DLQ;
  - ack só no sucesso; mensagem sem `message_id` ganha um gerado (`gen-<uuid>`) para rastreio.
  - **Pesquisar:** *quorum queues e x-delivery-limit*, *ack vs nack vs reject*, *poison messages*, *connect_robust*.

---

## Fase 6: ponta a ponta ✅

- [x] `make up` sobe RabbitMQ + scraper do zero.
- [x] Publicar `{"source":"pciconcursos"}` no exchange `gabarit.commands` com routing key `scrape.requested` (management em `http://localhost:15672`, usuário/senha no `.env`).
- [x] Os `exam.discovered` chegam numa fila de teste ligada a `gabarit.events` e batem com o schema.
- [x] `make test s=scraper` passa (roda no container de dev, com `/contracts` montado).

---

## Fase 7: agendamento ✅

- [x] 🛠️ **Quem dispara o `scrape.requested`** (`infra/scheduler`: supercronic + `publish`; manual: `make scrape`) algumas vezes por dia. Um cron que só publica a mensagem, fora do scraper: o scraper continua só reagindo a comandos, e outros serviços podem ganhar jobs no mesmo agendador.
  - **Pronto quando:** a coleta roda sozinha no horário, cada disparo leva um `message_id` próprio, e dá para disparar à mão com o mesmo comando.

---

## Fase 8: fonte PCI pelo HTML ✅

Motivo: o MCP do PCI passou a devolver no máximo 30 concursos por chamada, sem paginação (2026-10-27: 56 coletados de ~700).

- [x] **Investigação** (o que cada página traz):
  - `/cargos/` (223 cargos) e `/vagas/` (200, só os com 6+ concursos) se completam: a união dá ~248;
  - `/cargos/<slug>`: concursos **abertos** com o **id do PCI** na classe `nNNNNNN` (o mesmo `id` do MCP: identidade não mudou) e os nomes crus dos cargos que batem;
  - `/vagas/<slug>`: cards com UF, inscrições, vagas e escolaridade, sem id. `div.na`/`div.da` = abertos, `div.ea` = encerrados. Algumas páginas vêm quebradas do site (título sem o cargo);
  - as duas se ligam pelo link da notícia; sem teto nos abertos (Professor: 293).
- [x] **`src/infrastructure/sources/pci_html.py`**: parsers puros (`parse_cargos_index`, `parse_vagas_index`, `merge_catalog`, `parse_cargo_page`, `parse_vagas_page`, `parse_registration`, `build_exams`) e o adapter `PciHtmlSource`:
  - uma requisição por vez, `SCRAPER_REQUEST_DELAY_S` entre elas (vale para qualquer fonte) (padrão 1s), User-Agent identificado; 429/5xx/rede → novas tentativas com backoff e `Retry-After`; 404 → página pulada;
  - tudo ou nada: se uma página falha de vez, a coleta inteira falha (publicar com cargos faltando inativaria cargos no `core`).
- [x] **Catálogo de cargos**: `CatalogPosition` (domínio), evento `position_catalog.discovered` (contrato novo) publicado antes dos concursos; cada `Position` leva `catalog_slugs` (em quais cargos do catálogo o nome cru apareceu).
- [x] **Port** `ExamSource.collect()` → `SourceCollection(exams, catalog)`.
- [x] Saiu o MCP (`pci_mcp.py`, `mcp` do requirements); cron foi para 2x/dia (~500 requisições por coleta).
- [x] Testes com páginas reais em `tests/fixtures/pci_html/` e HTTP falso (`httpx.MockTransport`).

> ✋ **Revisão:** implementada pelo Claude. Pontos para ler: `parse_registration` (formatos de data do site) e o "tudo ou nada" do adapter.

---

## Depois

- **Cobertura:** concurso cujo nenhum cargo está no catálogo do PCI fica de fora. Medir de vez em quando contra o total do site.
- **Região de concurso nacional:** o card não diz; hoje `uf` e `region` ficam vazios (o MCP mandava "NACIONAL").
- **Atraso entre tentativas:** hoje o erro transitório tenta de novo na hora (3x) e vai pra DLQ; com o cron 2x/dia, a próxima coleta cobre a perdida. Vale fazer (filas de espera com TTL por degrau, ex.: 30s/2min/10min, header `x-retry-count`) se a DLQ começar a receber comandos por queda da fonte, ou quando entrarem fontes de bancas.
- **Documentos:** a página da notícia do PCI (HTML público, `httpx` resolve) traz o título do edital, um ID estável (`data-link`) e o link oficial da banca. Isso vira referência de documento no evento (sem baixar o PDF do PCI).
- **Fontes das bancas** (FGV, Cebraspe...): uma `ExamSource` por banca, com `httpx` + `selectolax` (`selectolax.lexbor`) quando o HTML vem pronto e Playwright (async) quando a página precisa de JavaScript.

---

## Erros clássicos para evitar

- Importar `infrastructure` dentro de `domain` ou `application`.
- Chamar lib síncrona (`requests`, `time.sleep`, Selenium) dentro de código async; isso trava o event loop.
- Fazer o ack antes de terminar o processamento; você perde a mensagem se o container cair no meio.
- Usar o horário sem timezone no evento; use sempre UTC.
- Testes unitários que fazem requisição de verdade.
- Normalizar o nome do cargo no scraper ("Analista Judiciário – Área Judiciária" vs "Analista Judiciário - Judiciária"). O scraper manda o nome cru, e a padronização entre bancas fica no `core`.
- Persistir `datas.aberto` ou `dias_restantes` do PCI: mudam todo dia. Calcule "aberto" a partir das datas.
- Contornar captcha ou proteção anti-bot de uma fonte.
