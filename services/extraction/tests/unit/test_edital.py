from src.domain.edital import EditalPart, edital_parts, pages_to_send

BODY, SYLLABUS, DUTIES, OTHER = (
    EditalPart.BODY,
    EditalPart.SYLLABUS,
    EditalPart.DUTIES,
    EditalPart.OTHER_ANNEX,
)

TEXT = "regras do concurso " * 30


def annex(number: str, title: str) -> str:
    return f"ANEXO {number}\n{title}\n" + "conteúdo do anexo " * 30


def test_anexo_no_meio_da_pagina_deixa_a_pagina_com_as_duas_partes() -> None:
    pages = [
        TEXT,
        TEXT + "\n" + annex("I", "CONTEÚDO PROGRAMÁTICO DAS PROVAS"),
        annex("II", "ATRIBUIÇÕES DOS CARGOS/ESPECIALIDADES"),
        annex("III", "MODELO DE LAUDO") + "\n" + annex("IV", "CRONOGRAMA PREVISTO"),
    ]

    assert edital_parts(pages) == [
        {BODY},
        {BODY, SYLLABUS},
        {DUTIES},
        {OTHER},
    ]


def test_titulo_no_topo_da_pagina_nao_conta_a_parte_anterior() -> None:
    pages = [TEXT, "Página 2 de 3\n" + annex("I", "CONTEÚDO PROGRAMÁTICO"), TEXT]

    assert edital_parts(pages) == [{BODY}, {SYLLABUS}, {SYLLABUS}]


def test_titulo_na_mesma_linha_e_palavras_quebradas_pelo_pdf() -> None:
    pages = [
        TEXT,
        "ANEXO 1 – CONTEÚ DO PROGRAMÁ TICO\n" + TEXT,
        "ANEXO 2 - QUADRO DE VAGAS E ATRIBUIÇÕES\n" + TEXT,
    ]

    # vagas no título: fica, mesmo falando de atribuições
    assert edital_parts(pages) == [{BODY}, {SYLLABUS}, {OTHER}]


def test_mencao_e_sumario_nao_sao_titulo() -> None:
    pages = [
        "SUMÁRIO\nANEXO I – CONTEÚDO PROGRAMÁTICO ........ 3\n" + TEXT,
        TEXT + "\nconforme o Anexo I deste edital\n" + "Anexo II\n" + TEXT,
        annex("I", "CONTEÚDO PROGRAMÁTICO"),
    ]

    assert edital_parts(pages) == [{BODY}, {BODY}, {SYLLABUS}]


def test_na_duvida_nao_recorta() -> None:
    # sem anexos
    assert edital_parts([TEXT, TEXT]) is None
    # escaneado
    assert edital_parts([TEXT, "", "", annex("I", "CONTEÚDO PROGRAMÁTICO")]) is None
    # anexo na primeira página
    assert edital_parts([annex("I", "CONTEÚDO PROGRAMÁTICO"), TEXT]) is None
    # anexos fora de ordem
    assert (
        edital_parts([TEXT, annex("II", "CRONOGRAMA"), annex("I", "CONTEÚDO PROGRAMÁTICO")]) is None
    )
    # lista de anexos colados, sem pontilhado: sumário ou anexos curtos? não dá para saber
    assert edital_parts([TEXT, "ANEXO I\nPROGRAMA\nANEXO II\nCRONOGRAMA\n" + TEXT]) is None


def test_paginas_a_mandar() -> None:
    parts = [{BODY}, {BODY, SYLLABUS}, {SYLLABUS}, {DUTIES}, {OTHER}]
    keep = {BODY, OTHER}

    assert pages_to_send([frozenset(p) for p in parts], keep) == [0, 1, 4]
    # nada a cortar
    assert pages_to_send([frozenset({BODY}), frozenset({OTHER})], keep) is None
