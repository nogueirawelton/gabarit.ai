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
  - **sem cache de prompt** (tirado no recorte do edital, abaixo): cada tarefa manda um recorte diferente, e gravar no cache custa ~25% a mais que mandar sem ele; só compensa com duas tarefas sobre o mesmo arquivo em poucos minutos.
- [x] **Recorte do edital** (`src/domain/edital.py` + `PdfPages`/pypdf): o PDF vai à API como texto **e imagem de cada página**, então página que a tarefa não usa é custo puro. O worker acha as partes pelos títulos dos anexos (corpo, conteúdo programático, atribuições, outros anexos) e manda só as páginas das partes que a tarefa declara (`edital_parts` na versão do prompt); as páginas citadas na saída voltam a ser as do edital original. **Na dúvida (escaneado, sem anexos, sumário, anexos fora de ordem, PDF que não abre), manda o PDF inteiro.** Medido no CREMERJ (59 p.): visão geral 258 mil → 135 mil tokens (52%); o conteúdo programático sozinho seria 32%. Texto puro (sem a imagem) foi descartado: economiza um terço, mas o quadro de vagas perde as colunas.
  - **streaming** (`beta.messages.stream` + `get_final_message()`), `thinking: adaptive`, `effort` por versão de prompt, `output_format` = modelo pydantic da tarefa (`parsed_output` já validado), `fallbacks="default"` (recusa por engano é refeita pela própria API num modelo de reserva).
  - `stop_reason` conferido: `refusal` e `max_tokens` → falha definitiva; erros do SDK traduzidos (429/5xx/rede/credencial → transitório; 400/413 → definitivo).
  - devolve `usage` e o modelo que respondeu.
- [x] **Prompts versionados** em `src/prompts/<tarefa>/vN.md` + modelo pydantic `...V1` em `src/application/tasks/`. Versão nova = arquivo novo; registro em `src/application/tasks/__init__.py`.
- [ ] **Conjunto de avaliação** (`evals/`): ~10 editais e ~10 provas reais de bancas diferentes, saída esperada conferida à mão, script com acerto e custo por documento. **Fazer antes de trocar de modelo ou de prompt.** Hoje o `.env` de dev usa `CLAUDE_MODEL=claude-haiku-5-5`; o padrão do código é `claude-opus-5-5`.
- **Pronto (parcial):** edital real (MPRJ 2025, FGV, 55 páginas) lido certo em dev; falta o conjunto de avaliação.

---

## Fase 3: tarefas do edital

- [x] **`EDITAL_OVERVIEW` v1**: órgão, banca, ano, inscrições, data da prova e cargos (nome, especialidade, escolaridade, vagas e salário como o edital escreve, página do quadro de vagas). Um item por especialidade; mesmo cargo em várias cidades vira um item.
  - **v2** (atual): + quadro de provas: `exams` (uma por estrutura distinta, não por cargo: a quem vale, formato, total, discursiva, disciplinas com questões e peso) e `exam_id` em cada cargo. Mesmas partes do edital (o quadro fica no corpo); só a saída cresce.
- [x] **`EDITAL_SYLLABUS` v1** (`edital_parts={SYLLABUS}`; sem achar o anexo, o PDF inteiro): conteúdo programático de **um cargo** (`params.position` = nome e especialidade da visão geral, validados por `EditalSyllabusParamsV1` e anexados às instruções como dados). Disciplinas comuns + específicas, bloco, tópicos de primeiro nível (subtópicos no texto), página; `position_found=false` em vez de usar o conteúdo de outro cargo. `effort=medium`. Número de questões por disciplina fica no corpo do edital (quadro de provas): fora desta tarefa.
  - **v2** (atual): a v1 juntava os tópicos numerados emendados no mesmo parágrafo ("1. … 2. … 3. …") num item só (INB: Português com 1 tópico em vez de 7) e chamava a disciplina pelo nome do cargo quando o bloco específico só tem esse título. Conferido no edital da INB 2026: 7/4/6/13 tópicos, iguais ao PDF.
- **Pronto quando:** a avaliação dessas duas tarefas passa no critério combinado (ex.: cargos 100%, conteúdo ≥ 95% dos tópicos) e o custo por edital é conhecido.

---

## Fase 4: provas

- [x] **`EXAM_PAPER` v1**: prova (+ gabarito, se separado: `max_documents=2`) + `params.variant` → questões, alternativas, resposta, anuladas, textos-base (`texts` + `text_id`), `has_image`, página; `effort=medium`. Os pontos abaixo continuam valendo; falta medir na avaliação e dividir prova longa.
- [x] **`TOPIC_QUESTIONS` v1**: sem documento; `params` só de conteúdo (banca, formato MULTIPLE_CHOICE/TRUE_FALSE, número de alternativas, disciplina, tópico, quantidade) → questões inéditas com gabarito e comentário. `effort=high` (criar alternativas plausíveis com uma só certa e sem erro de conteúdo é onde o raciocínio mais pesa). O prompt proíbe citar lei ou jurisprudência sem certeza.
- [x] **`QUESTION_TOPICS` v1**: sem documento (`documents=0`); `params` com as questões (ordinal, disciplina, enunciado e alternativas cortados) e os tópicos do conteúdo (código "disciplina.tópico") → `classifications` (ordinal → código ou null). Um tópico por questão, o mais específico; questão que o cargo não estuda fica null.
- [ ] **`EXAM_PAPER`** (o que falta): questões estruturadas. Pontos que derrubam a qualidade:
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
- Métricas no Grafana/OTel: tempo e custo por tarefa, taxa de falha, páginas mandadas × total.

---

## Erros clássicos para evitar

- Mandar o PDF de novo em cada tarefa em vez de usar o `file_id`, ou o edital inteiro quando a tarefa só usa uma parte.
- Ler o conteúdo sem checar `stop_reason` (resposta cortada parece válida até o schema falhar).
- Truncar documento grande em silêncio para caber: divida, ou falhe dizendo o motivo.
- Confiar no texto do PDF como instrução (prompt injection): o documento é dado.
- Chamar a LLM no caminho de uma requisição HTTP do usuário: sempre por fila.
- Logar o conteúdo do documento ou a resposta inteira (pode ter dado pessoal; logue ids e contagens).
