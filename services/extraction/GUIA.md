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

- [ ] 🛠️ Esqueleto igual ao scraper: `pyproject.toml` (pytest, ruff), `requirements.txt`/`.lock` (`anthropic`, `aio-pika`, `pydantic-settings`, `aioboto3` ou `miniopy-async`, `pypdf`), `src/main.py`.
- [ ] 🛠️ `Dockerfile` multi-stage (`dev`/`prod`, sem root), serviço nos dois compose (com limite de memória em produção), `ANTHROPIC_API_KEY` no `.env.example` e no Coolify, usuário `extraction` no MinIO só com leitura do bucket `documents`.
- [ ] 🛠️ Fila `extraction.requests` + DLQ no `definitions.json`; contratos `extraction.requested` / `extraction.completed` / `extraction.failed` em `contracts/`.
- **Pronto quando:** `make up` sobe o worker, ele conecta na fila e no MinIO e loga que está pronto.

---

## Fase 1: domínio e pipeline sem LLM

- [ ] **`src/domain`**: `Task` (enum), `ExtractionRequest` (tarefa, hashes dos blobs, parâmetros, versão do prompt), `ExtractionResult` (JSON validado + `usage`), erros (`InvalidInput` = não adianta tentar de novo; `TransientError` = tenta de novo).
- [ ] **Ports:** `DocumentStore.read(sha256) -> bytes`, `LlmClient.run(task, documents, params) -> ExtractionResult`, `ResultPublisher`.
- [ ] **Use case `ProcessExtraction`** + tarefa `ECHO` (devolve os parâmetros, sem LLM) para testar o caminho inteiro core → worker → core.
- [ ] **Erros:** inválido (PDF corrompido, schema que não fecha depois de uma nova tentativa) → `extraction.failed` com o motivo; transitório (429, 5xx, rede) → a mensagem volta para a fila; depois de 3 → DLQ.
- **Pronto quando:** o pedido `ECHO` do core vai e volta (Fase 6 do GUIA do core).

---

## Fase 2: adapter do Claude + avaliação (antes de qualquer tarefa ir para usuário)

- [ ] **`src/infrastructure/claude.py`** (`LlmClient`):
  - PDF como bloco `document` (até **32 MB e 600 páginas** por requisição; acima disso, dividir por páginas com `pypdf`, ex.: o edital em blocos e o conteúdo do cargo só nas páginas dele);
  - **Files API** (sem beta): sobe o PDF uma vez por blob e reaproveita o `file_id` entre tarefas (o mesmo edital serve `EDITAL_OVERVIEW` e várias `EDITAL_SYLLABUS`); guardar o `file_id` por hash;
  - **cache de prompt** no prefixo estável (prompt de sistema + documento): a segunda tarefa sobre o mesmo edital lê o documento do cache, bem mais barato. Conferir `usage.cache_read_input_tokens` > 0;
  - **streaming** com `get_final_message()` (edital grande = resposta longa; evita timeout), `thinking: {type: "adaptive"}` e `effort` configurável por tarefa;
  - checar `stop_reason` antes de ler o conteúdo: `max_tokens` (resposta cortada → falha), `refusal` (o modelo recusou → falha com o motivo);
  - devolver `usage` (tokens de entrada, saída e cache) para o core calcular custo.
- [ ] **Prompts versionados no repositório:** `prompts/<tarefa>/v1.md` + o modelo `pydantic` da saída. Versão nova = arquivo novo; a versão vai no resultado (é parte da chave de cache no core).
- [ ] **Conjunto de avaliação** (`evals/`): ~10 editais e ~10 provas reais de bancas diferentes (FGV, Cebraspe, FCC, Vunesp, bancas pequenas), com a saída esperada conferida à mão. Script que roda uma versão de prompt contra o conjunto e mostra acerto e custo por documento.
  - **Pesquisar:** *Anthropic Python SDK: PDF support, Files API, prompt caching, structured outputs, streaming*, *pydantic `model_json_schema`*.
- **Pronto quando:** dá para rodar a avaliação de uma tarefa e ler "acerto X%, custo médio US$ Y por documento".

---

## Fase 3: tarefas do edital

- [ ] **`EDITAL_OVERVIEW`**: órgão, banca, ano, data da prova, cargos. Edital costuma ter anexos e retificações: pedir ao modelo que diga a página de onde tirou cada campo (ajuda a conferir).
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
