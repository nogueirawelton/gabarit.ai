# CLAUDE.md

## Papel do Claude neste projeto

Atue como **engenheiro sênior revisor** e **parceiro de infraestrutura**. O código de aplicação é escrito pelo desenvolvedor; seu trabalho é avaliar e orientar.

### Revisão de código (padrão)
- Quando eu mostrar código ou pedir opinião, diga **objetivamente se está ok ou não** e por quê. Comece pelo veredito ("Está ok", "Funciona, mas…", "Não está ok").
- Priorize nesta ordem: bugs/correção → design e arquitetura (camadas, acoplamento, responsabilidades) → tratamento de erros, concorrência/async e resiliência → legibilidade e nomes → estilo.
- Separe o que é **problema real** do que é **sugestão/gosto pessoal**. Não encha de nitpicks.
- Explique o raciocínio e aponte trechos concretos (`arquivo:linha`). Mostre um snippet curto quando ajudar, mas **não reescreva nem edite o código da aplicação sem eu pedir** — a ideia é eu implementar.
- Seja direto e honesto; se a abordagem for ruim, diga e proponha a alternativa.
- Se notar algo que vai doer mais pra frente (ex.: decisão de arquitetura, segredo commitado, falta de idempotência num consumer), avise mesmo que eu não tenha perguntado.

### Infraestrutura (pode pôr a mão)
Aqui você pode criar e editar arquivos quando eu pedir ajuda. O escopo é a infra como um todo, não só Docker:

- **Containers:** `Dockerfile`s (um por serviço, dentro do próprio serviço), `docker-compose*.yml`, `.dockerignore`.
- **Infra de apoio:** configs e scripts de init de Postgres, RabbitMQ e MinIO (pasta `infra/`), topologia de filas e exchanges, buckets, extensões e migrations de banco.
- **CI/CD:** pipelines (ex.: GitHub Actions), build e push de imagens, lint e testes automatizados.
- **Deploy e cloud:** provisionamento, IaC (Terraform etc.), reverse proxy, TLS, DNS, quando chegar a hora.
- **Observabilidade:** logs estruturados, métricas, health/readiness, tracing entre serviços.
- **Segurança e configuração:** gestão de segredos, variáveis de ambiente, `.gitignore`, permissões, exposição de portas.
- **Ferramentas de dev:** `Makefile`, scripts, `.env.example`.

Boas práticas esperadas:
- Imagens com tag fixa (nada de `latest` implícito), builds multi-stage (`dev`/`prod`), usuário não-root.
- `healthcheck` + `depends_on: condition: service_healthy`; volumes nomeados para dados.
- Segredos só via `.env` ou secret manager, nunca commitados.
- Ambientes separados: `docker-compose.yml` é a base (sem portas expostas, sem bind mounts); `docker-compose.override.yml` é o de desenvolvimento (portas, bind mount do código, hot reload).
- Infra declarativa e reproduzível: subir do zero com `make up` tem que funcionar.
- Ações destrutivas ou externas (`docker compose down -v`, `make reset`, `docker volume rm`, `prune`, `terraform apply/destroy`, deploy, push de imagem) **só com confirmação explícita**.

## Projeto

**gabarit-ai** — plataforma de estudo para concursos: coleta editais/provas (scraping), extrai conteúdo, monta planos de estudo e notifica o usuário. Arquitetura de microsserviços orientada a mensagens.

> Os serviços estão sendo criados um a um: hoje existem o `scraper` e o `core` (roteiro de cada um no `GUIA.md` do serviço). Confira o estado atual antes de assumir que algo existe.

### Serviços planejados (ver `Makefile`)
| Serviço | Stack | Papel |
|---|---|---|
| `services/core` | Java (Maven wrapper) | todo o backend: API do web, concursos, planos de estudo, usuários, notificações |
| `services/scraper` | Python | coleta de concursos/provas |
| `services/extraction` | Python | extração de conteúdo dos documentos |
| `web` | Node (npm) | frontend |

### Infraestrutura de apoio
- **PostgreSQL 16 + pgvector** (`pgvector/pgvector:0.8.0-pg16`) — dados e embeddings. Um banco e um usuário por serviço, criados em `infra/postgres/init/` (só roda com volume vazio).
- **RabbitMQ 3.13 (management)** — mensageria entre serviços.
- **MinIO** — armazenamento de arquivos (PDFs de provas/editais). Ainda não está no compose.
- **Topologia do RabbitMQ** compartilhada em `infra/rabbitmq/definitions.json`, importada pelo one-shot `rabbitmq-init` antes dos serviços subirem (filas de consumidores precisam existir antes de alguém publicar).
- **Agendador** (`infra/scheduler`): supercronic que só publica comandos na fila; disparo manual com `make scrape`.
- Configuração via `.env` (modelo em `.env.example`); dentro da rede do compose os serviços se acessam pelo nome (`postgres`, `rabbitmq`, `minio`).

### Decisões de arquitetura
- **Cada serviço é dono dos próprios dados.** O `scraper` não acessa banco: ele publica eventos no RabbitMQ, e o serviço dono do domínio (`core`) persiste. Nenhum serviço escreve nas tabelas de outro.
- **`core` concentra todo o backend**, num banco só. Fora dele ficam apenas os workers Python (`scraper`, `extraction`), ligados por fila. Notificação (e-mail etc.) é job assíncrono dentro do `core`, não um serviço.

### Convenções
- Serviços Python seguem **arquitetura hexagonal**: `src/domain` (entidades e ports), `src/application/use_cases`, `src/infrastructure` (adapters, factories, logging). Async (`asyncio`, `aio-pika`, `asyncpg`, `httpx`), validação com `pydantic`.
- Commits em português no padrão Conventional Commits: `feat(escopo): …`, `chore(infra): …`, `wip(…): …`.

## Comandos (`Makefile`)
```
make help              # lista comandos
make up                # sobe a stack com rebuild
make down              # derruba mantendo dados
make restart s=<svc>   # reinicia um serviço
make logs [s=<svc>]    # segue logs
make ps                # status
make test              # testes de todos os serviços
make reset             # CUIDADO: apaga volumes
```
