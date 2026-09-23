"""
Livro Vivo - Trilha da Leitura - Gerador de conteúdo
====================================================

Este script gera o arquivo "trilha.json" que o Livro Vivo lê no modo
"Aprender a ler".

Como usar:
    1. Instale o Python 3 (versão 3.8 ou mais nova).
    2. Abra o terminal na pasta deste arquivo.
    3. Rode:  python gerar_conteudo.py
    4. Copie o "trilha.json" gerado para:  app/src/main/assets/alfabetizacao/

Para mudar o conteúdo (palavras, letras etc.), edite as listas da
seção "CONTEÚDO" logo abaixo e rode o script de novo.

O script não precisa de nenhuma biblioteca extra: usa só o que já
vem com o Python.
"""

import json
import random
from collections import Counter

# =====================================================================
# CONFIGURAÇÕES
# =====================================================================

ARQUIVO_SAIDA = "trilha.json"
ARQUIVO_IMAGENS = "lista_imagens.txt"
VERSAO_CONTEUDO = 1
PERGUNTAS_POR_FASE = 5
FASES_GRATIS_NOS_MODULOS_PAGOS = 2  # nos módulos 3, 4 e 5
SEMENTE_ALEATORIA = 42  # mesma semente = mesmo resultado toda vez

# =====================================================================
# CONTEÚDO (edite aqui)
# =====================================================================

VOGAIS = ["A", "E", "I", "O", "U"]

# Palavra de exemplo para cada vogal (usada com figura)
EXEMPLO_VOGAL = {
    "A": "ABELHA",
    "E": "ELEFANTE",
    "I": "ILHA",
    "O": "OVO",
    "U": "UVA",
}

CONSOANTES = ["B", "C", "D", "F", "G", "L", "M", "N", "P", "R", "S", "T", "V"]

# Palavra de exemplo para cada consoante (usada com figura)
EXEMPLO_CONSOANTE = {
    "B": "BOLA",
    "C": "CASA",
    "D": "DADO",
    "F": "FACA",
    "G": "GATO",
    "L": "LATA",
    "M": "MALA",
    "N": "NAVE",
    "P": "PATO",
    "R": "RATO",
    "S": "SAPO",
    "T": "TATU",
    "V": "VACA",
}

# 50 palavras de 2 sílabas simples (consoante + vogal).
# Escreva separando as sílabas com hífen.
# São 10 fases x 5 palavras nos módulos "Palavras" e "Ditado".
PALAVRAS = [
    "BO-LA", "CA-SA", "GA-TO", "PA-TO", "MA-LA",
    "SA-PO", "DA-DO", "FA-CA", "VA-CA", "BO-CA",
    "CA-MA", "CO-PO", "DE-DO", "FO-GO", "GA-LO",
    "LA-TA", "LO-BO", "MO-LA", "NA-VE", "PE-NA",
    "PI-PA", "RO-DA", "RA-TO", "SA-CO", "SE-LO",
    "TA-TU", "TU-BO", "VE-LA", "BA-LA", "BO-LO",
    "BU-LE", "CO-LA", "FA-DA", "FI-TA", "LU-PA",
    "MA-PA", "ME-SA", "MO-TO", "NA-BO", "PE-RA",
    "SU-CO", "TE-LA", "VA-SO", "FO-CA", "DO-CE",
    "LU-VA", "RE-DE", "SO-PA", "GE-LO", "TO-CA",
]

# Palavras de apoio: palavrinhas muito usadas que a criança aprende "de vista"
# (não precisam ser formadas só por sílabas simples). Os livros "Eu leio"
# podem usá-las desde o início.
PALAVRAS_DE_APOIO = [
    "O", "A", "E", "É", "UM", "UMA", "OS", "AS",
    "DE", "DO", "DA", "NO", "NA", "COM", "EM", "NÃO", "SIM",
    "TEM", "ESTÁ", "SÃO",
]

# Palavras femininas da lista PALAVRAS (usam o artigo "A").
# As que não estão aqui usam o artigo "O".
PALAVRAS_FEMININAS = {
    "BOLA", "CASA", "MALA", "FACA", "VACA", "BOCA", "CAMA", "LATA", "MOLA",
    "NAVE", "PENA", "PIPA", "RODA", "VELA", "BALA", "COLA", "FADA", "FITA",
    "LUPA", "MESA", "MOTO", "PERA", "TELA", "FOCA", "LUVA", "REDE", "SOPA",
    "TOCA",
}

# Palavras que fazem sentido depois de "ESTÁ NO" / "ESTÁ NA" nos livros "Eu leio":
# lugares e coisas onde algo pode ficar dentro ou em cima ("O GATO ESTÁ NA CAMA").
LUGARES = {
    "CASA", "CAMA", "MESA", "MALA", "SACO", "COPO", "LATA", "VASO", "BULE",
    "TUBO", "LUVA", "REDE", "TOCA", "NAVE", "MOTO",
}

# Palavras que NÃO entram em frases de posse ou de lugar nos livros "Eu leio"
# ("LIA TEM UM ...", "O ... É DE LIA", "O ... ESTÁ NA ..."): partes do corpo,
# coisas perigosas para criança e lugares que não se carregam.
# Elas continuam aparecendo em frases como "É UMA BOCA!".
NAO_OBJETOS = {
    "BOCA", "DEDO", "FOGO", "FACA", "CASA", "TOCA",
}

# =====================================================================
# FUNÇÕES DE APOIO
# =====================================================================


def nome_imagem(palavra):
    """Transforma 'BOLA' em 'img_bola' (nome do desenho no Android)."""
    return "img_" + palavra.lower()


def separar_silabas(palavra_com_hifen):
    """Transforma 'BO-LA' em ['BO', 'LA']."""
    return palavra_com_hifen.split("-")


def juntar_palavra(palavra_com_hifen):
    """Transforma 'BO-LA' em 'BOLA'."""
    return palavra_com_hifen.replace("-", "")


def montar_opcoes(resposta, possibilidades, quantidade=3):
    """
    Cria uma lista de opções com a resposta certa + alternativas erradas,
    tudo embaralhado. Ex.: resposta 'A' -> ['E', 'A', 'O']
    """
    erradas = [item for item in possibilidades if item != resposta]
    erradas = list(dict.fromkeys(erradas))  # remove repetidos
    escolhidas = random.sample(erradas, min(quantidade - 1, len(erradas)))
    opcoes = escolhidas + [resposta]
    random.shuffle(opcoes)
    return opcoes


def embaralhar(lista):
    """Devolve uma cópia embaralhada da lista (sem mudar a original)."""
    copia = list(lista)
    random.shuffle(copia)
    return copia


def nova_pergunta(tipo, fala, resposta, opcoes=None, pecas=None, imagem=None):
    """Cria o dicionário de uma pergunta no formato que o app espera."""
    return {
        "tipo": tipo,
        "fala": fala,
        "resposta": resposta,
        "opcoes": opcoes if opcoes is not None else [],
        "pecas": pecas if pecas is not None else [],
        "imagem": imagem,
    }


def todas_as_silabas():
    """Lista todas as sílabas simples: BA, BE, BI, ..., VU."""
    return [c + v for c in CONSOANTES for v in VOGAIS]


# =====================================================================
# MÓDULO 1 - VOGAIS
# =====================================================================


def gerar_modulo_vogais():
    fases = []
    for indice, vogal in enumerate(VOGAIS):
        exemplo = EXEMPLO_VOGAL[vogal]
        # vogais já aprendidas (para revisão)
        aprendidas = VOGAIS[: indice + 1]

        perguntas = [
            nova_pergunta(
                "ouvir_tocar",
                f"Toque na letra {vogal}",
                vogal,
                opcoes=montar_opcoes(vogal, VOGAIS),
            ),
            nova_pergunta(
                "ouvir_tocar",
                f"{exemplo.capitalize()} começa com qual letra?",
                vogal,
                opcoes=montar_opcoes(vogal, VOGAIS),
                imagem=nome_imagem(exemplo),
            ),
            nova_pergunta(
                "ouvir_tocar",
                f"Cadê a letra {vogal}?",
                vogal,
                opcoes=montar_opcoes(vogal, VOGAIS),
            ),
        ]

        # 2 perguntas de revisão com vogais já aprendidas
        for _ in range(PERGUNTAS_POR_FASE - len(perguntas)):
            revisar = random.choice(aprendidas)
            perguntas.append(
                nova_pergunta(
                    "ouvir_tocar",
                    f"Toque na letra {revisar}",
                    revisar,
                    opcoes=montar_opcoes(revisar, VOGAIS),
                )
            )

        fases.append(
            {
                "id": f"vogais_{vogal.lower()}",
                "titulo": f"Letra {vogal}",
                "gratis": True,
                "ensina": [vogal],
                "perguntas": perguntas,
            }
        )

    return {"id": "vogais", "titulo": "Vogais", "ordem": 1, "fases": fases}


# =====================================================================
# MÓDULO 2 - CONSOANTES
# =====================================================================


def gerar_modulo_consoantes():
    fases = []
    for indice, consoante in enumerate(CONSOANTES):
        exemplo = EXEMPLO_CONSOANTE[consoante]
        aprendidas = CONSOANTES[: indice + 1]

        perguntas = [
            nova_pergunta(
                "ouvir_tocar",
                f"Toque na letra {consoante}",
                consoante,
                opcoes=montar_opcoes(consoante, CONSOANTES),
            ),
            nova_pergunta(
                "ouvir_tocar",
                f"{exemplo.capitalize()} começa com qual letra?",
                consoante,
                opcoes=montar_opcoes(consoante, CONSOANTES),
                imagem=nome_imagem(exemplo),
            ),
            nova_pergunta(
                "ouvir_tocar",
                f"Cadê a letra {consoante}?",
                consoante,
                opcoes=montar_opcoes(consoante, CONSOANTES),
            ),
        ]

        for _ in range(PERGUNTAS_POR_FASE - len(perguntas)):
            revisar = random.choice(aprendidas)
            perguntas.append(
                nova_pergunta(
                    "ouvir_tocar",
                    f"Toque na letra {revisar}",
                    revisar,
                    opcoes=montar_opcoes(revisar, CONSOANTES),
                )
            )

        fases.append(
            {
                "id": f"consoantes_{consoante.lower()}",
                "titulo": f"Letra {consoante}",
                "gratis": True,
                "ensina": [consoante],
                "perguntas": perguntas,
            }
        )

    return {"id": "consoantes", "titulo": "Consoantes", "ordem": 2, "fases": fases}


# =====================================================================
# MÓDULO 3 - SÍLABAS
# =====================================================================


def gerar_modulo_silabas():
    fases = []
    for indice, consoante in enumerate(CONSOANTES):
        silabas_da_fase = [consoante + vogal for vogal in VOGAIS]  # BA BE BI BO BU
        perguntas = []

        for numero, vogal in enumerate(VOGAIS):
            silaba = consoante + vogal
            if numero % 2 == 0:
                # Perguntas 1, 3 e 5: juntar as letras
                perguntas.append(
                    nova_pergunta(
                        "juntar",
                        f"Junte as letras e forme {silaba}",
                        silaba,
                        pecas=embaralhar([consoante, vogal]),
                    )
                )
            else:
                # Perguntas 2 e 4: ouvir e tocar na sílaba
                perguntas.append(
                    nova_pergunta(
                        "ouvir_tocar",
                        f"Toque na sílaba {silaba}",
                        silaba,
                        opcoes=montar_opcoes(silaba, silabas_da_fase),
                    )
                )

        fases.append(
            {
                "id": f"silabas_{consoante.lower()}",
                "titulo": f"Sílabas com {consoante}",
                "gratis": indice < FASES_GRATIS_NOS_MODULOS_PAGOS,
                "ensina": silabas_da_fase,
                "perguntas": perguntas,
            }
        )

    return {"id": "silabas", "titulo": "Sílabas", "ordem": 3, "fases": fases}


# =====================================================================
# MÓDULO 4 - PRIMEIRAS PALAVRAS
# =====================================================================


def gerar_modulo_palavras():
    fases = []
    todas_palavras = [juntar_palavra(p) for p in PALAVRAS]

    for numero_fase in range(len(PALAVRAS) // PERGUNTAS_POR_FASE):
        inicio = numero_fase * PERGUNTAS_POR_FASE
        grupo = PALAVRAS[inicio : inicio + PERGUNTAS_POR_FASE]
        perguntas = []

        for numero, palavra_hifen in enumerate(grupo):
            palavra = juntar_palavra(palavra_hifen)
            silabas = separar_silabas(palavra_hifen)

            if numero % 2 == 0:
                # Montar a palavra com as sílabas embaralhadas
                pecas = embaralhar(silabas)
                # garante que as peças não fiquem já na ordem certa
                while pecas == silabas and len(set(silabas)) > 1:
                    pecas = embaralhar(silabas)
                perguntas.append(
                    nova_pergunta(
                        "montar_palavra",
                        f"Monte a palavra {palavra.lower()}",
                        palavra,
                        pecas=pecas,
                        imagem=nome_imagem(palavra),
                    )
                )
            else:
                # Ver a figura e escolher a palavra escrita
                perguntas.append(
                    nova_pergunta(
                        "figura_palavra",
                        "Qual é o nome desta figura?",
                        palavra,
                        opcoes=montar_opcoes(palavra, todas_palavras),
                        imagem=nome_imagem(palavra),
                    )
                )

        fases.append(
            {
                "id": f"palavras_{numero_fase + 1:02d}",
                "titulo": f"Palavras {numero_fase + 1}",
                "gratis": numero_fase < FASES_GRATIS_NOS_MODULOS_PAGOS,
                "ensina": [juntar_palavra(p) for p in grupo],
                "perguntas": perguntas,
            }
        )

    return {"id": "palavras", "titulo": "Primeiras palavras", "ordem": 4, "fases": fases}


# =====================================================================
# MÓDULO 5 - DITADO
# =====================================================================


def gerar_modulo_ditado():
    fases = []
    # usa as mesmas palavras, mas em outra ordem
    palavras_embaralhadas = embaralhar(PALAVRAS)
    silabas_existentes = todas_as_silabas()

    for numero_fase in range(len(palavras_embaralhadas) // PERGUNTAS_POR_FASE):
        inicio = numero_fase * PERGUNTAS_POR_FASE
        grupo = palavras_embaralhadas[inicio : inicio + PERGUNTAS_POR_FASE]
        perguntas = []

        for palavra_hifen in grupo:
            palavra = juntar_palavra(palavra_hifen)
            silabas = separar_silabas(palavra_hifen)

            # 2 sílabas extras (erradas) para deixar o ditado mais desafiador
            extras_possiveis = [s for s in silabas_existentes if s not in silabas]
            extras = random.sample(extras_possiveis, 2)

            perguntas.append(
                nova_pergunta(
                    "ditado",
                    f"Escreva a palavra {palavra.lower()}",
                    palavra,
                    pecas=embaralhar(silabas + extras),
                )
            )

        fases.append(
            {
                "id": f"ditado_{numero_fase + 1:02d}",
                "titulo": f"Ditado {numero_fase + 1}",
                "gratis": numero_fase < FASES_GRATIS_NOS_MODULOS_PAGOS,
                "ensina": [],
                "perguntas": perguntas,
            }
        )

    return {"id": "ditado", "titulo": "Ditado", "ordem": 5, "fases": fases}


# =====================================================================
# VALIDAÇÃO (procura erros antes de gerar o arquivo)
# =====================================================================


def da_para_formar(resposta, pecas):
    """
    Verifica se dá para formar a 'resposta' juntando algumas das 'pecas',
    usando cada peça no máximo uma vez.
    Ex.: da_para_formar('BOLA', ['LA', 'TE', 'BO']) -> True
    """
    if resposta == "":
        return True
    for i, peca in enumerate(pecas):
        if peca and resposta.startswith(peca):
            restantes = pecas[:i] + pecas[i + 1 :]
            if da_para_formar(resposta[len(peca) :], restantes):
                return True
    return False


def validar(conteudo):
    """Devolve uma lista de erros encontrados (lista vazia = tudo certo)."""
    erros = []
    ids_vistos = set()
    tipos_validos = {"ouvir_tocar", "juntar", "montar_palavra", "figura_palavra", "ditado"}

    # Toda palavra feminina precisa existir na lista PALAVRAS
    todas = {juntar_palavra(p) for p in PALAVRAS}
    sobrando = PALAVRAS_FEMININAS - todas
    if sobrando:
        erros.append(f"PALAVRAS_FEMININAS tem palavras fora da lista: {sorted(sobrando)}")

    # LUGARES e NAO_OBJETOS também precisam existir na lista PALAVRAS
    for nome, conjunto in (("LUGARES", LUGARES), ("NAO_OBJETOS", NAO_OBJETOS)):
        fora = conjunto - todas
        if fora:
            erros.append(f"{nome} tem palavras fora da lista: {sorted(fora)}")

    # Palavras repetidas na lista?
    repetidas = [p for p, qtd in Counter(PALAVRAS).items() if qtd > 1]
    if repetidas:
        erros.append(f"Palavras repetidas na lista PALAVRAS: {repetidas}")

    # Cada sílaba das palavras precisa ser consoante + vogal conhecidas
    for palavra_hifen in PALAVRAS:
        for silaba in separar_silabas(palavra_hifen):
            if len(silaba) != 2 or silaba[0] not in CONSOANTES or silaba[1] not in VOGAIS:
                erros.append(f"Sílaba '{silaba}' em '{palavra_hifen}' não é consoante + vogal da lista")

    for modulo in conteudo["modulos"]:
        for fase in modulo["fases"]:
            if fase["id"] in ids_vistos:
                erros.append(f"ID de fase repetido: {fase['id']}")
            ids_vistos.add(fase["id"])

            if len(fase["perguntas"]) != PERGUNTAS_POR_FASE:
                erros.append(f"Fase {fase['id']} tem {len(fase['perguntas'])} perguntas")

            for n, pergunta in enumerate(fase["perguntas"], start=1):
                local = f"{fase['id']} pergunta {n}"
                tipo = pergunta["tipo"]

                if tipo not in tipos_validos:
                    erros.append(f"{local}: tipo desconhecido '{tipo}'")

                if not pergunta["fala"].strip():
                    erros.append(f"{local}: fala vazia")

                if tipo in ("ouvir_tocar", "figura_palavra"):
                    if pergunta["resposta"] not in pergunta["opcoes"]:
                        erros.append(f"{local}: resposta não está nas opções")
                    if len(pergunta["opcoes"]) < 2:
                        erros.append(f"{local}: precisa de pelo menos 2 opções")
                    if len(set(pergunta["opcoes"])) != len(pergunta["opcoes"]):
                        erros.append(f"{local}: opções repetidas")

                if tipo in ("juntar", "montar_palavra", "ditado"):
                    if not da_para_formar(pergunta["resposta"], pergunta["pecas"]):
                        erros.append(f"{local}: não dá para formar a resposta com as peças")

                if tipo == "figura_palavra" and not pergunta["imagem"]:
                    erros.append(f"{local}: figura_palavra precisa de imagem")

    return erros


# =====================================================================
# PROGRAMA PRINCIPAL
# =====================================================================


def listar_imagens(conteudo):
    """Junta todos os nomes de imagem usados, sem repetir, em ordem alfabética."""
    imagens = set()
    for modulo in conteudo["modulos"]:
        for fase in modulo["fases"]:
            for pergunta in fase["perguntas"]:
                if pergunta["imagem"]:
                    imagens.add(pergunta["imagem"])
    return sorted(imagens)


def main():
    random.seed(SEMENTE_ALEATORIA)

    conteudo = {
        "versao": VERSAO_CONTEUDO,
        "palavras_de_apoio": PALAVRAS_DE_APOIO,
        "vocabulario": [
            {
                "palavra": juntar_palavra(p),
                "silabas": separar_silabas(p),
                "imagem": nome_imagem(juntar_palavra(p)),
                "artigo": "A" if juntar_palavra(p) in PALAVRAS_FEMININAS else "O",
                "objeto": juntar_palavra(p) not in NAO_OBJETOS,
                "lugar": juntar_palavra(p) in LUGARES,
            }
            for p in PALAVRAS
        ],
        "modulos": [
            gerar_modulo_vogais(),
            gerar_modulo_consoantes(),
            gerar_modulo_silabas(),
            gerar_modulo_palavras(),
            gerar_modulo_ditado(),
        ],
    }

    erros = validar(conteudo)
    if erros:
        print("ATENÇÃO: foram encontrados erros. O arquivo NÃO foi gerado.")
        for erro in erros:
            print(" -", erro)
        return

    with open(ARQUIVO_SAIDA, "w", encoding="utf-8") as arquivo:
        json.dump(conteudo, arquivo, ensure_ascii=False, indent=2)

    imagens = listar_imagens(conteudo)
    with open(ARQUIVO_IMAGENS, "w", encoding="utf-8") as arquivo:
        arquivo.write("\n".join(imagens) + "\n")

    # Resumo na tela
    total_fases = 0
    total_perguntas = 0
    print("Conteúdo gerado com sucesso!\n")
    for modulo in conteudo["modulos"]:
        qtd_fases = len(modulo["fases"])
        qtd_perguntas = sum(len(f["perguntas"]) for f in modulo["fases"])
        qtd_gratis = sum(1 for f in modulo["fases"] if f["gratis"])
        total_fases += qtd_fases
        total_perguntas += qtd_perguntas
        print(
            f"  {modulo['ordem']}. {modulo['titulo']:<20} "
            f"{qtd_fases:>2} fases ({qtd_gratis} grátis), {qtd_perguntas} perguntas"
        )
    print(f"\n  Total: {total_fases} fases, {total_perguntas} perguntas")
    print(f"  Arquivo salvo: {ARQUIVO_SAIDA}")
    print(f"  Imagens necessárias ({len(imagens)}): veja {ARQUIVO_IMAGENS}")


if __name__ == "__main__":
    main()
