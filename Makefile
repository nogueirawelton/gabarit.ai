.PHONY: help up down restart logs ps test reset

SERVICES = exam plan scraper extraction notification web

TEST_exam         = cd services/exam && ./mvnw -q test
TEST_plan         = cd services/plan && ./mvnw -q test
TEST_scraper      = cd services/scraper && pytest
TEST_extraction   = cd services/extraction && pytest
TEST_notification = cd services/notification && pytest
TEST_web          = cd web && npm test

help:            ## lista os comandos disponíveis
	@grep -E "^[a-zA-Z_-]+:.*##" $(MAKEFILE_LIST) | awk -F ":.*## " "{printf \"  %-10s %s\\n\", \$$1, \$$2}"

up:              ## sobe a stack (rebuild das imagens)
	docker compose up --build

down:            ## derruba a stack, mantendo os dados
	docker compose down

restart:         ## reinicia um serviço: make restart s=exam
	docker compose restart $(s)

logs:            ## segue logs (todos ou um): make logs s=exam
	docker compose logs -f $(s)

ps:              ## status dos containers
	docker compose ps

test:            ## roda testes (todos ou um): make test s=scraper
	$(if $(s),$(if $(TEST_$(s)),,$(error serviço desconhecido: '$(s)'. Opções: $(SERVICES))))
	@set -e; $(foreach svc,$(or $(s),$(SERVICES)),echo "==> $(svc)"; ($(TEST_$(svc)));)

reset:           ## CUIDADO: derruba tudo e apaga os volumes (dados)
	docker compose down -v
