# Guia do extraction

Roteiro do serviço, na ordem de implementação. Cada item diz **o que o arquivo faz**, **o que pesquisar** e **quando está pronto**. Marque o checkbox ao terminar e peça revisão ao fim de cada fase.

Itens com 🛠️ são infra: peça para o Claude fazer.

**Papel:** o worker que conversa com a LLM (Claude). Recebe `extraction.requested` da fila, lê os PDFs do MinIO, chama a API do Claude com o prompt da tarefa, valida o JSON de saída contra o schema e publica `extraction.completed` (ou `extraction.failed`). Não tem banco: quem guarda resultado, cache e custo é o `core` (Fase 6 do GUIA do core).

**Tarefas** (cada uma = um prompt versionado + um schema de saída):

| Tarefa | Entrada | Saída |
|---|---|---|
| `EDITAL_OVERVIEW` | edital | órgão, banca, ano, data da prova, cargos (nome, escolaridade, vagas) |
| `EDITAL_SYLLABUS` | edital + cargo | disciplinas → tópicos, peso/nº de questões, tipo de prova |
| `EXAM_PAPER` | prova + gabarito + tipo/cor + conteúdo do cargo | questões, alternativas ou certo/errado, resposta, anulada, tópico |
| `STUDY_PRIORITIES` | conteúdo + desempenho + banca | prioridade e peso por tópico (para o plano montado no core) |

**Decisões que moldam o desenho:**
- **Hexagonal, como o scraper:** `src/domain` (tarefas, resultados, ports), `src/application/use_cases`, `src/infrastructure` (Claude, MinIO, RabbitMQ). Async (`aio-pika`), `pydantic` para os schemas.
- **SDK oficial `anthropic` (Python).** Nada de HTTP na mão nem bibliotecas de terceiros por cima.
- **Modelo:** `claude-opus-5-5` (padrão do projeto), configurável por variável (`CLAUDE_MODEL`). Trocar por um modelo mais barato numa tarefa é decisão de produto, tomada **depois** de medir com a avaliação da Fase 2 que a qualidade se mantém.
- **Saída sempre estruturada:** cada tarefa tem um modelo `pydantic`, e a resposta vem no formato do schema (*structured outputs*: `output_config.format` / `client.messages.parse()`). Resultado que não valida é falha, não "quase certo".
- **O PDF é dado, não instrução.** Os arquivos vêm de usuários e podem conter texto tentando mudar o comportamento do modelo (prompt injection). O prompt de sistema diz que o documento é só material a extrair; a saída estruturada limita o estrago; nada do conteúdo é executado ou usado como comando.
- **Sem segredo no core:** `ANTHROPIC_API_KEY` só existe neste serviço.

---

## Fase 0: preparação

- [x] 🛠️ Esqueleto igual ao scraper: `pyproject.toml` (pytest, ruff), `requirements.txt`/`.lock` (`aio-pika`, `pydantic-settings`, `minio`), `src/main.py`. `anthropic` e `pypdf` entram na Fase 2, quando forem usados.
- [x] 🛠️ `Dockerfile` multi-stage (`dev`/`prod`, sem root), serviço nos dois compose (384 MB em produção), `ANTHROPIC_API_KEY`/`CLAUDE_MODEL` no `.env.example` (cadastrar no Coolify), usuário `extraction` no MinIO só com `GetObject` em `documents/blobs/*`.
  - O cliente `minio` recebe a região fixa: sem ela ele chama `GetBucketLocation`, que a policy não libera (tudo vira `AccessDenied`).
- [x] 🛠️ Fila `extraction.requests` + DLQ no `definitions.json` (declarada pela infra; o worker só confere, passivo); contratos `extraction.requested` / `extraction.completed` / `extraction.failed` em `contracts/`.
- **Pronto:** `make up` sobe o worker, ele confere o MinIO (credencial errada derruba o boot), conecta na fila e loga `extraction pronto`.

---

## Fase 1: domínio e pipeline sem LLM

- [x] **`src/domain`**: `Task` + `PROMPT_VERSIONS` (versões que o worker sabe executar), `DocumentRef`, `ExtractionOutput` (JSON validado + `Usage` + modelo), erros (`InvalidInputError` = não adianta tentar de novo; `TransientError` = tenta de novo).
- [x] **Ports:** `DocumentStore.read(key) -> bytes`, `EventPublisher`. O `LlmClient` entra na Fase 2, com o primeiro adapter.
- [x] **Use case `ProcessExtraction`** + tarefa `ECHO` (lê os PDFs, confere o sha256 e devolve os parâmetros, sem LLM).
- [x] **Erros:** inválido (documento ausente, hash que não confere, versão de prompt desconhecida) → `extraction.failed` com `retryable=false`; transitório → a mensagem volta para a fila; na última entrega (`x-delivery-count`) o worker desiste e publica `extraction.failed` com `retryable=true`, para o core não esperar para sempre. DLQ fica só para mensagem malformada.
- **Pronto:** o pedido `ECHO` vai e volta (Fase 6 do GUIA do core), testado no compose.

---

## Fase 2: adapter do Claude + avaliação (antes de qualquer tarefa ir para usuário)

- [x] **`src/infrastructure/llm/claude.py`** (`LlmClient`, SDK `anthropic` 2.x, async):
  - PDF pela **Files API** (estável, sem beta): sobe uma vez por sha256 com `expires_in_seconds` (24h; expira sozinho na Anthropic, sem limpeza nossa) e reaproveita o `file_id` em memória até ~80% da validade. Também resolve o limite de 32 MB da requisição: o PDF de 30 MB em base64 não caberia.
  - **cache de prompt** no último documento; o prompt de sistema (`src/prompts/system.md`) é o mesmo em todas as tarefas, então a 2ª tarefa sobre o mesmo edital lê o PDF do cache. Conferido: 172 mil tokens lidos do cache na nova tentativa.
  - **streaming** (`beta.messages.stream` + `get_final_message()`), `thinking: adaptive`, `effort` por versão de prompt, `output_format` = modelo pydantic da tarefa (`parsed_output` já validado), `fallbacks="default"` (recusa por engano é refeita pela própria API num modelo de reserva).
  - `stop_reason` conferido: `refusal` e `max_tokens` → falha definitiva; erros do SDK traduzidos (429/5xx/rede/credencial → transitório; 400/413 → definitivo).
  - devolve `usage` e o modelo que respondeu.
- [x] **Prompts versionados** em `src/prompts/<tarefa>/vN.md` + modelo pydantic `...V1` em `src/application/tasks/`. Versão nova = arquivo novo; registro em `src/application/tasks/__init__.py`.
- [ ] **Conjunto de avaliação** (`evals/`): ~10 editais e ~10 provas reais de bancas diferentes, saída esperada conferida à mão, script com acerto e custo por documento. **Fazer antes de trocar de modelo ou de prompt.** Hoje o `.env` de dev usa `CLAUDE_MODEL=claude-haiku-5-5`; o padrão do código é `claude-opus-5-5`.
- **Pronto (parcial):** edital real (MPRJ 2025, FGV, 55 páginas) lido certo em dev; falta o conjunto de avaliação.

---

## Fase 3: tarefas do edital

- [x] **`EDITAL_OVERVIEW` v1**: órgão, banca, ano, inscrições, data da prova e cargos (nome, especialidade, escolaridade, vagas e salário como o edital escreve, página do quadro de vagas). Um item por especialidade; mesmo cargo em várias cidades vira um item.
- [ ] **`EDITAL_SYLLABUS`**: conteúdo do cargo escolhido. Atenção a conteúdo **comum a vários cargos** ("Conhecimentos Gerais, para todos os cargos") somado ao específico.
- **Pronto quando:** a avaliação dessas duas tarefas passa no critério combinado (ex.: cargos 100%, conteúdo ≥ 95% dos tópicos) e o custo por edital é conhecido.

---

## Fase 4: provas

- [ ] **`EXAM_PAPER`**: questões estruturadas. Pontos que derrubam a qualidade:
  - **tipo/cor da prova:** o gabarito tem uma coluna por tipo; usar a coluna do tipo informado (ou detectado no cabeçalho da prova);
  - **formatos:** A–E, A–D, certo/errado (Cebraspe), discursivas e redação (marcar e pular no simulado);
  - **anuladas e alteradas** pelo gabarito definitivo;
  - **texto-base compartilhado** ("Leia o texto para responder às questões 1 a 5") e imagens/tabelas (guardar a página);
  - **prova longa:** dividir por páginas e juntar, sem duplicar nem perder questão na divisa.
- **Pronto quando:** na avaliação, número de questões e respostas batem 100% com o gabarito, e o tópico bate na maioria (meta combinada).

---

## Fase 5: prioridades para o plano

- [ ] **`STUDY_PRIORITIES`**: a partir do conteúdo, do desempenho nos simulados e da frequência dos tópicos nas provas da banca, devolver prioridade e peso por tópico. A agenda (dias e horas) é montada no core, sem LLM (decisão da Fase 9 do GUIA do core).
- **Pronto quando:** dois alunos com desempenhos diferentes recebem prioridades diferentes e coerentes.

---

## Depois

- **Batches API** (metade do preço) para reprocessar documentos quando um prompt melhora.
- Métricas no Grafana/OTel: tempo e custo por tarefa, taxa de falha, cache de prompt.

---

## Erros clássicos para evitar

- Mandar o PDF de novo em cada tarefa em vez de usar o `file_id` e o cache de prompt.
- Ler o conteúdo sem checar `stop_reason` (resposta cortada parece válida até o schema falhar).
- Truncar documento grande em silêncio para caber: divida, ou falhe dizendo o motivo.
- Confiar no texto do PDF como instrução (prompt injection): o documento é dado.
- Chamar a LLM no caminho de uma requisição HTTP do usuário: sempre por fila.
- Logar o conteúdo do documento ou a resposta inteira (pode ter dado pessoal; logue ids e contagens).
