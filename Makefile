.PHONY: help up down restart logs ps test lock scrape reset

SERVICES = core scraper extraction web

PYTHON_SERVICES = scraper extraction

DIR_web = web
svc_dir = $(or $(DIR_$(1)),services/$(1))

# Rodam no container de dev; --no-deps: não precisam da stack.
# Python: deps e /contracts já montados. core: Testcontainers sobe postgres/rabbitmq próprios via socket do Docker
TEST_core       = docker compose run --rm --no-deps -v /var/run/docker.sock:/var/run/docker.sock core ./mvnw -q -B test
TEST_scraper    = docker compose run --rm --no-deps scraper pytest
TEST_extraction = docker compose run --rm --no-deps extraction pytest
TEST_web        = cd web && npm test

help:            ## lista os comandos disponíveis
	@grep -E "^[a-zA-Z_-]+:.*##" $(MAKEFILE_LIST) | awk -F ":.*## " "{printf \"  %-10s %s\\n\", \$$1, \$$2}"

up:              ## sobe a stack (rebuild das imagens)
	docker compose up --build

down:            ## derruba a stack, mantendo os dados
	docker compose down

restart:         ## reinicia um serviço: make restart s=core
	docker compose restart $(s)

logs:            ## segue logs (todos ou um): make logs s=core
	docker compose logs -f $(s)

ps:              ## status dos containers
	docker compose ps

test:            ## roda testes (todos ou um): make test s=scraper
	$(if $(s),$(if $(TEST_$(s)),,$(error serviço desconhecido: '$(s)'. Opções: $(SERVICES))))
	@set -e; $(foreach svc,$(or $(s),$(SERVICES)),echo "==> $(svc)"; \
		if [ -d $(call svc_dir,$(svc)) ]; then ($(TEST_$(svc))); else echo "    (ainda não existe, pulando)"; fi;)

lock:            ## regenera os locks de dependências Python (precisa do uv no host): make lock s=scraper
	@set -e; $(foreach svc,$(or $(s),$(PYTHON_SERVICES)),if [ -d services/$(svc) ]; then echo "==> $(svc)"; \
		cd services/$(svc) \
		&& uv pip compile requirements.txt -o requirements.lock --python-version 3.13 --python-platform linux -q \
		&& uv pip compile requirements-dev.txt -c requirements.lock -o requirements-dev.lock --python-version 3.13 --python-platform linux -q \
		&& cd - >/dev/null; fi;)

scrape:          ## dispara uma coleta agora (mesmo comando do cron): make scrape [source=pciconcursos]
	docker compose run --rm --no-deps scheduler publish gabarit.commands scrape.requested '{"source":"$(or $(source),pciconcursos)"}'

reset:           ## CUIDADO: derruba tudo e apaga os volumes (dados)
	docker compose down -v
