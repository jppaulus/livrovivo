"""
Livro Vivo - Trilha da Leitura - Gravador das falas fixas
=========================================================

Grava, uma vez só, com a voz do Capitão Aventura no Gemini (Puck), tudo o que a
trilha fala sempre do mesmo jeito:

    fala     as instruções das perguntas ("Toque na sílaba BE")
    letra    as letras, pelo nome ("B" -> "bê")
    silaba   as sílabas ("BA" -> "bá")
    palavra  as palavras dos livros "Eu leio" (vocabulário, palavras de apoio e
             os companheiros que a criança consegue ler)
    frase    as frases fixas das telas ("Muito bem!", "Tente de novo!"...)

Os áudios vão para app/src/main/assets/voz/, junto com o índice indice.json que o
app lê. Assim a trilha fala na hora, sem internet e sem chave nenhuma no aparelho.
Enquanto uma fala não foi gravada, o app usa a voz de narração normal.

Como usar:
    1. Google Cloud (padrão, para a loja): faturamento ativo no projeto, a API "Cloud Text-to-Speech"
       ativada e o Google Cloud CLI instalado, com login feito uma vez no terminal:
           gcloud auth application-default login
       (essas vozes não aceitam chave de API). O projeto vem do gcloud ou da linha
       googlecloud.projeto=... no local.properties.
       AI Studio (--provedor gemini, só para testes): gemini.apiKey=AIza... no local.properties.
    2. Abra o terminal na pasta deste arquivo e rode:
           python gerar_audios.py --teste       (mostra o que falta gravar, sem chamar nada)
           python gerar_audios.py               (grava tudo o que ainda não existe)
           python gerar_audios.py --tipo silaba (só um tipo: fala, letra, silaba, palavra ou frase)
           python gerar_audios.py --so BA,BOLA  (só estas falas; vale o texto ou a chave "silaba:BA")
           python gerar_audios.py --refazer     (grava de novo o que já existe)
    3. Ouça tudo em ferramentas/revisao_audios.html (o script cria essa página).
       Se alguma fala ficou estranha, grave de novo com --so e --refazer.

Limites: no plano gratuito, a voz do Gemini aceita só 3 pedidos por minuto e poucos
por dia (em 23/09/2026 acabou depois de uns 5). Com o faturamento ativo no AI Studio,
gravar tudo custa menos de US$ 1. Quando o limite do dia acaba, o script para e
guarda o que já gravou; rode de novo no dia seguinte e ele continua de onde parou.
Com --lote 18, várias falas vão num pedido só e o áudio é cortado nos silêncios
(economiza pedidos, mas ouça tudo com atenção depois).

Precisa do ffmpeg instalado (converte para .ogg, que é bem menor). Sem ele, os
arquivos saem em .wav, que funcionam mas deixam o app bem maior.

A chave nunca vai para o repositório (o local.properties está no .gitignore).
"""

import argparse
import array
import base64
import html
import io
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import wave

# =====================================================================
# CONFIGURAÇÕES
# =====================================================================

PASTA = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(PASTA)
ARQUIVO_TRILHA = os.path.join(RAIZ, "app", "src", "main", "assets", "alfabetizacao", "trilha.json")
ARQUIVO_PROPRIEDADES = os.path.join(RAIZ, "local.properties")
PASTA_VOZ = os.path.join(RAIZ, "app", "src", "main", "assets", "voz")
ARQUIVO_INDICE = os.path.join(PASTA_VOZ, "indice.json")
PAGINA_REVISAO = os.path.join(PASTA, "revisao_audios.html")

# Voz do Capitão Aventura no Gemini (a mesma do app: VoicePersona.AVENTUREIRO).
VOZ_GEMINI = "Puck"
MODELOS_GEMINI = ["gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"]

# Google Cloud Text-to-Speech (Gemini-TTS): as mesmas vozes do Gemini, com termos que permitem app infantil.
# É o caminho para os áudios que vão para a loja. O modelo estável (GA) pode ir para produção; os de
# prévia ("-preview") o Google Cloud só libera para testes.
MODELO_CLOUD = "gemini-2.5-flash-tts"
URL_CLOUD = "https://texttospeech.googleapis.com/v1/text:synthesize"

# Agent Platform (antigo Vertex AI), do Google Cloud: o modelo 3.1 em prévia é liberado para produção por
# aqui (exceção listada nos termos de produtos em prévia do Google Cloud). Em 24/09/2026 foi o mais fiel
# nos testes: o 2.5 repetiu "Muito bem" e embolou "bola".
MODELO_VERTEX = "gemini-3.1-flash-tts-preview"
URL_VERTEX = "https://aiplatform.googleapis.com/v1/projects/{projeto}/locations/global/publishers/google/models/{modelo}:generateContent"
DOLARES_POR_MINUTO = {"gemini-2.5-flash-tts": 0.015, "gemini-2.5-pro-tts": 0.03, "gemini-3.1-flash-tts-preview": 0.03}

TIPOS = ["frase", "letra", "silaba", "palavra", "fala"]
PASTAS_DOS_TIPOS = {"fala": "falas", "letra": "letras", "silaba": "silabas", "palavra": "palavras", "frase": "frases"}

# Companheiros que entram nos livros quando a criança já lê o nome (LU-NA, PI-PO-CA).
COMPANHEIROS_LEGIVEIS = ["LUNA", "PIPOCA"]

# Frases fixas das telas da trilha. A mesma lista está em core/literacy/TrailPhrases.kt
# (um teste confere as duas): se mudar aqui, mude lá também.
FRASES_FIXAS = [
    "Muito bem!",
    "Tente de novo!",
    "Incrível! Você ganhou 3 estrelas!",
    "Muito bem! Você ganhou 2 estrelas!",
    "Boa! Você ganhou 1 estrela!",
    "Você treinou bastante! Vamos tentar de novo?",
    "Você ganhou um livro novo para ler sozinho!",
    "Esta fase faz parte da assinatura. Chame um adulto para ver com você!",
    "Termine a fase de antes para abrir esta.",
    "Parabéns! Você leu o livro todo! Quer ler de novo?",
]
# Uma por módulo, com o título dele no lugar das chaves.
FRASE_MODULO_TRANCADO = "Termine as fases de antes para abrir {}."

# Palavras de apoio que, sozinhas, a voz poderia ler como o nome da letra ("o" -> "ó").
# O contexto vai só para a direção de voz; ela fala apenas a palavra.
CONTEXTO_DAS_PALAVRAS = {
    "O": 'the article "o", as in "o gato" (sounds like "u")',
    "A": 'the article "a", as in "a bola"',
    "E": 'the word "e" meaning "and", as in "pão e leite" (sounds like "i")',
    "É": 'the verb "é", as in "ele é feliz"',
    "OS": 'the article "os", as in "os gatos"',
    "AS": 'the article "as", as in "as bolas"',
    "DE": 'the preposition "de", as in "copo de suco"',
    "DO": 'the word "do", as in "a casa do gato"',
    "DA": 'the word "da", as in "a casa da vaca"',
    "NO": 'the word "no", as in "no saco"',
    "NA": 'the word "na", as in "na mesa"',
    "EM": 'the preposition "em", as in "em casa"',
    "UM": 'the article "um", as in "um gato"',
    "UMA": 'the article "uma", as in "uma bola"',
    # Sozinha, "pena" saiu soletrada ("pê... na") em 24/09.
    "PENA": 'the noun "pena", a bird feather, said naturally as one word',
}

# =====================================================================
# PRONÚNCIA (a mesma regra do app: core/literacy/Pronunciation.kt)
# =====================================================================

NOMES_DAS_LETRAS = {
    "A": "á", "E": "é", "I": "i", "O": "ó", "U": "u",
    "B": "bê", "C": "cê", "D": "dê", "F": "éfe", "G": "gê", "H": "agá", "J": "jota",
    "K": "cá", "L": "éle", "M": "ême", "N": "êne", "P": "pê", "Q": "quê", "R": "érre",
    "S": "ésse", "T": "tê", "V": "vê", "W": "dáblio", "X": "xis", "Y": "ípsilon", "Z": "zê",
}
VOGAL_DA_SILABA = {"A": "á", "E": "ê", "I": "i", "O": "ô", "U": "u"}


def dica(unidade):
    """Como escrever a letra ou sílaba para a voz: 'B' -> 'bê', 'BA' -> 'bá'."""
    if unidade in NOMES_DAS_LETRAS:
        return NOMES_DAS_LETRAS[unidade]
    if len(unidade) == 2 and unidade[1] in VOGAL_DA_SILABA:
        return unidade[0].lower() + VOGAL_DA_SILABA[unidade[1]]
    return unidade.lower()


def falar_instrucao(texto, unidades):
    """
    'Toque na sílaba BE' -> 'Toque na sílaba bê.': a sílaba do fim vai escrita como se fala. Letra fica como
    letra ('Toque na letra F.'): escrito 'éfe', a voz soletrava o acento; 'F' ela lê 'éfe' sozinha.
    """
    achou = re.match(r"^(.*\S)\s+([A-ZÀ-Ý]{1,2})([.!?]*)$", texto.strip())
    if achou and achou.group(2) in unidades:
        unidade = achou.group(2)
        falado = unidade if len(unidade) == 1 else dica(unidade)
        return f"{achou.group(1)} {falado}{achou.group(3) or '.'}"
    return texto if texto.rstrip()[-1:] in ".!?" else texto + "."


# =====================================================================
# O QUE GRAVAR
# =====================================================================


def normalizar(texto):
    """Igual ao app (VoiceClips.normalize): acentos compostos, sem espaços sobrando."""
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", texto)).strip()


def chave(tipo, texto):
    texto = normalizar(texto)
    if tipo in ("letra", "silaba", "palavra"):
        texto = texto.upper()
    return f"{tipo}:{texto}"


DIRECOES_DOS_TIPOS = {
    "frase": "a short, warm sentence said to the child",
    "letra": "the NAME of one letter of the alphabet",
    "silaba": "one syllable, said as a single sound",
    "palavra": "one single word",
    "fala": "a short instruction in a learning game",
}


class Fala:
    """Uma fala a gravar: a chave no índice, o texto que a voz lê e, para palavras ambíguas, o contexto."""

    def __init__(self, tipo, texto, falado, contexto=None):
        self.tipo = tipo
        self.texto = texto
        self.chave = chave(tipo, texto)
        self.falado = falado
        self.contexto = contexto


def ler_falas():
    with open(ARQUIVO_TRILHA, encoding="utf-8") as arquivo:
        trilha = json.load(arquivo)
    letras, silabas, instrucoes = [], [], []
    for modulo in trilha["modulos"]:
        for fase in modulo["fases"]:
            if modulo["id"] in ("vogais", "consoantes"):
                letras += fase["ensina"]
            elif modulo["id"] == "silabas":
                silabas += fase["ensina"]
            for pergunta in fase["perguntas"]:
                if pergunta["fala"] not in instrucoes:
                    instrucoes.append(pergunta["fala"])
    unidades = set(letras) | set(silabas)

    falas = []
    frases = FRASES_FIXAS + [FRASE_MODULO_TRANCADO.format(m["titulo"]) for m in trilha["modulos"]]
    for frase in frases:
        falas.append(Fala("frase", frase, frase))
    for letra in dict.fromkeys(letras):
        falas.append(Fala("letra", letra, letra,
                          f'the name of the letter {letra}, as Brazilian children say it when learning the alphabet '
                          f'("{dica(letra)}")'))
    for silaba in dict.fromkeys(silabas):
        falas.append(Fala("silaba", silaba, dica(silaba)))
    palavras = [v["palavra"] for v in trilha["vocabulario"]] + trilha["palavras_de_apoio"] + COMPANHEIROS_LEGIVEIS
    for palavra in dict.fromkeys(palavras):
        contexto = CONTEXTO_DAS_PALAVRAS.get(palavra)
        falas.append(Fala("palavra", palavra, palavra.lower(), f"it is {contexto}; say only the word" if contexto else None))
    for instrucao in instrucoes:
        falas.append(Fala("fala", instrucao, falar_instrucao(instrucao, unidades)))
    return falas


# =====================================================================
# ÍNDICE E ARQUIVOS
# =====================================================================


def ler_indice():
    if os.path.exists(ARQUIVO_INDICE):
        with open(ARQUIVO_INDICE, encoding="utf-8") as arquivo:
            return json.load(arquivo)
    return {"voz": VOZ_GEMINI, "clips": {}}


def salvar_indice(indice):
    os.makedirs(PASTA_VOZ, exist_ok=True)
    indice["clips"] = dict(sorted(indice["clips"].items()))
    temporario = ARQUIVO_INDICE + ".tmp"
    with open(temporario, "w", encoding="utf-8") as arquivo:
        json.dump(indice, arquivo, ensure_ascii=False, indent=1)
        arquivo.write("\n")
    os.replace(temporario, ARQUIVO_INDICE)


def nome_do_arquivo(fala, indice, extensao):
    """Nome legível e estável: 'Toque na sílaba BE' -> falas/toque_na_silaba_be.ogg ('É' vira e_2 se 'E' já existe)."""
    atual = indice["clips"].get(fala.chave)
    if atual and atual.endswith(extensao):
        return atual
    sem_acento = unicodedata.normalize("NFD", fala.texto)
    sem_acento = "".join(c for c in sem_acento if unicodedata.category(c) != "Mn")
    base = re.sub(r"[^a-z0-9]+", "_", sem_acento.lower()).strip("_")[:60] or "fala"
    pasta = PASTAS_DOS_TIPOS[fala.tipo]
    usados = set(indice["clips"].values())
    candidato, numero = f"{pasta}/{base}{extensao}", 2
    while candidato in usados:
        candidato = f"{pasta}/{base}_{numero}{extensao}"
        numero += 1
    return candidato


def existe(fala, indice):
    arquivo = indice["clips"].get(fala.chave)
    return bool(arquivo) and os.path.exists(os.path.join(PASTA_VOZ, arquivo))


# =====================================================================
# ÁUDIO
# =====================================================================


def amostras_de(pcm):
    dados = array.array("h")
    dados.frombytes(pcm[: len(pcm) // 2 * 2])
    if sys.byteorder != "little":
        dados.byteswap()
    return dados


def bytes_de(amostras):
    dados = array.array("h", amostras)
    if sys.byteorder != "little":
        dados.byteswap()
    return dados.tobytes()


def aparar_e_nivelar(pcm, taxa):
    """Tira o silêncio das pontas (com uma folguinha) e deixa todas as falas com o mesmo volume."""
    amostras = amostras_de(pcm)
    fortes = [i for i, a in enumerate(amostras) if abs(a) > 600]
    if not fortes:
        return pcm, 0.0
    inicio = max(fortes[0] - int(taxa * 0.06), 0)
    fim = min(fortes[-1] + int(taxa * 0.15), len(amostras))
    trecho = amostras[inicio:fim]
    rms = math.sqrt(sum(a * a for a in trecho) / len(trecho)) or 1.0
    pico = max(abs(a) for a in trecho) or 1
    ganho = min(3162.0 / rms, 29000.0 / pico)  # alvo: -20 dBFS de média, pico abaixo de -1 dBFS
    nivelado = [int(max(-32768, min(32767, a * ganho))) for a in trecho]
    return bytes_de(nivelado), len(trecho) / taxa


def gravar_arquivo(pcm, taxa, destino):
    """Converte para .ogg com o ffmpeg; sem ffmpeg, grava .wav."""
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    if destino.endswith(".ogg"):
        subprocess.run(
            ["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(taxa), "-ac", "1", "-i", "pipe:0",
             "-c:a", "libvorbis", "-q:a", "3", destino],
            input=pcm, check=True,
        )
    else:
        with wave.open(destino, "wb") as saida:
            saida.setnchannels(1)
            saida.setsampwidth(2)
            saida.setframerate(taxa)
            saida.writeframes(pcm)


def duracao_esperada(texto):
    """Segundos aproximados para falar o texto devagar (para achar falas com algo a mais)."""
    return 0.4 + len(texto) / 11.0


QUADRO = 0.02  # segundos por quadro na procura de silêncios


def trechos_de_fala(pcm, taxa):
    """Energia de cada quadro de 20 ms e quais quadros têm voz."""
    amostras = amostras_de(pcm)
    passo = int(taxa * QUADRO)
    energia = []
    for i in range(0, len(amostras) - passo + 1, passo):
        quadro = amostras[i:i + passo]
        energia.append(math.sqrt(sum(a * a for a in quadro) / passo))
    if not energia:
        return [], []
    referencia = sorted(energia)[int(len(energia) * 0.9)]
    limiar = max(150.0, referencia * 0.06)
    return energia, [e > limiar for e in energia]


def separar(pcm, taxa, esperados):
    """
    Corta a gravação de um lote em uma fala por item, nos silêncios mais longos.
    Devolve a lista de pedaços (PCM), ou None se a separação não ficou confiável.
    """
    n = len(esperados)
    _, voz = trechos_de_fala(pcm, taxa)
    if not any(voz):
        return None
    primeiro = voz.index(True)
    ultimo = len(voz) - 1 - voz[::-1].index(True)
    silencios = []  # (duração em quadros, início, fim)
    i = primeiro
    while i <= ultimo:
        if not voz[i]:
            j = i
            while j <= ultimo and not voz[j]:
                j += 1
            silencios.append((j - i, i, j))
            i = j
        else:
            i += 1
    if n == 1:
        cortes = []
    else:
        if len(silencios) < n - 1:
            return None
        ordenados = sorted(silencios, reverse=True)
        cortes = sorted(ordenados[: n - 1], key=lambda s: s[1])
        menor_corte = ordenados[n - 2][0] * QUADRO
        maior_resto = ordenados[n - 1][0] * QUADRO if len(ordenados) >= n else 0.0
        # Entre falas o silêncio pedido é longo; dentro de uma fala, só pausas curtas.
        if menor_corte < 0.45 or (maior_resto and menor_corte < 1.4 * maior_resto):
            return None
    # Pontas com folga: o começo e o fim bem baixinhos de cada fala não podem ser cortados.
    limites = [max(0, primeiro - 5)] + [(ini + fim) // 2 for _, ini, fim in cortes] + [min(len(voz), ultimo + 11)]
    passo = int(taxa * QUADRO)
    pedacos = [pcm[a * passo * 2:b * passo * 2] for a, b in zip(limites, limites[1:])]

    # Cada pedaço precisa ter um tamanho coerente com o texto (senão a separação trocou falas).
    duracoes = []
    for (a, b), esperado in zip(zip(limites, limites[1:]), esperados):
        falado = sum(1 for k in range(a, b) if voz[k]) * QUADRO
        duracoes.append(falado)
    escala = sum(duracoes) / sum(esperados)
    for falado, esperado in zip(duracoes, esperados):
        razao = falado / (esperado * escala)
        if razao < 0.35 or razao > 2.8:
            return None
    return pedacos


# =====================================================================
# GEMINI
# =====================================================================


class LimiteDoDia(Exception):
    pass


class ErroDeConfiguracao(Exception):
    """Problema na conta (API desativada, chave sem permissão, sem faturamento): não adianta tentar as outras falas."""


def limites_estourados(detalhe):
    """Quais cotas o erro 429 diz que acabaram (ex.: GenerateRequestsPerMinutePerProjectPerModel-FreeTier)."""
    try:
        detalhes = json.loads(detalhe)["error"].get("details", [])
    except (ValueError, KeyError, AttributeError):
        return []
    return [v.get("quotaId", "") for d in detalhes for v in d.get("violations", [])]


def chamar(url, cabecalhos, corpo):
    """Chamada HTTP; em erro 429 espera o tempo que o serviço pedir. Cota do dia esgotada vira LimiteDoDia."""
    dados = json.dumps(corpo).encode("utf-8")
    for tentativa in range(8):
        pedido = urllib.request.Request(url, data=dados, headers=cabecalhos, method="POST")
        try:
            with urllib.request.urlopen(pedido, timeout=300) as resposta:
                return json.loads(resposta.read())
        except urllib.error.HTTPError as erro:
            detalhe = erro.read().decode("utf-8", "replace")
            if erro.code == 429:
                limites = limites_estourados(detalhe)
                if any("PerDay" in limite for limite in limites):
                    raise LimiteDoDia(", ".join(limites)) from erro
                pedido_de_espera = re.search(r'"retryDelay":\s*"(\d+)', detalhe)
                espera = int(pedido_de_espera.group(1)) + 2 if pedido_de_espera else 20 * (tentativa + 1)
                print(f"   limite por minuto ({', '.join(limites) or 'sem detalhe'}); esperando {espera} s...")
                time.sleep(espera)
                continue
            if erro.code >= 500 and tentativa < 2:
                time.sleep(5)
                continue
            raise RuntimeError(f"HTTP {erro.code}: {detalhe[:300]}") from erro
        except (urllib.error.URLError, TimeoutError, ConnectionError) as erro:
            if tentativa < 2:
                time.sleep(5)
                continue
            raise RuntimeError(f"sem conexão ou sem resposta: {getattr(erro, 'reason', erro)}") from erro
    raise RuntimeError("o serviço continuou pedindo pausa")


PERFIL = """# AUDIO PROFILE: Capitão Aventura
## A warm, cheerful explorer who is teaching a young Brazilian child (4 to 7 years old) to read.

### DIRECTOR'S NOTES
Style: warm, kind and encouraging, like a favorite teacher. Smiling voice, never loud or shouty.
Pace: a little slower than normal conversation and very clear, but natural: never split words into syllables.
Accent: native Brazilian Portuguese (pt-BR)."""


def direcao_de_voz(falas):
    """Perfil e direção no formato do guia de voz do Gemini (sem o texto a falar)."""
    direcao = DIRECOES_DOS_TIPOS[falas[0].tipo]
    contextos = [f'- "{f.falado}": {f.contexto}.' for f in falas if f.contexto]
    notas = ("\nPronunciation notes:\n" + "\n".join(contextos)) if contextos else ""
    if len(falas) == 1:
        o_que = f"What to say: {direcao}. Say only the transcript below, exactly once, with nothing before or after it."
    else:
        o_que = (
            f"What to say: the transcript has {len(falas)} lines; each line is {direcao}. Read every line exactly once, "
            "in order, as a complete utterance on its own, with a calm ending (not like reading a list). "
            "After each line, stay completely silent for two full seconds. Never say numbers or add any words."
        )
    return f"{PERFIL}\n{o_que}{notas}"


def roteiro(falas):
    """Pedido do AI Studio: a direção e, depois dela, o texto a falar."""
    return direcao_de_voz(falas) + "\n\n### TRANSCRIPT\n" + "\n".join(f.falado for f in falas)


class CredencialCloud:
    """
    Login da conta Google pelo Google Cloud CLI: as vozes do Gemini no Cloud Text-to-Speech não aceitam chave
    de API, só um token de acesso. Uma vez, no terminal: gcloud auth application-default login
    """

    VALIDADE = 40 * 60  # o token vale 1 hora; renova antes

    def __init__(self, projeto):
        self.gcloud = shutil.which("gcloud") or shutil.which("gcloud.cmd") or next(
            (c for c in [
                os.path.expandvars(r"%LOCALAPPDATA%\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"),
                r"C:\Program Files (x86)\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd",
                r"C:\Program Files\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd",
            ] if os.path.exists(c)), None)
        if not self.gcloud:
            raise ErroDeConfiguracao("o Google Cloud CLI (gcloud) não está instalado")
        self.projeto = projeto or self._rodar("config", "get-value", "project")
        if not self.projeto:
            raise ErroDeConfiguracao("falta o projeto do Google Cloud (googlecloud.projeto no local.properties)")
        self.token, self.obtido = None, 0.0

    def _rodar(self, *argumentos):
        resultado = subprocess.run([self.gcloud, *argumentos], capture_output=True, text=True, timeout=120)
        return resultado.stdout.strip() if resultado.returncode == 0 else ""

    def cabecalhos(self, renovar=False):
        if renovar or not self.token or time.time() - self.obtido > self.VALIDADE:
            self.token = self._rodar("auth", "application-default", "print-access-token")
            if not self.token:
                raise ErroDeConfiguracao("sem login no Google Cloud (rode: gcloud auth application-default login)")
            self.obtido = time.time()
        return {"Authorization": f"Bearer {self.token}", "x-goog-user-project": self.projeto,
                "Content-Type": "application/json"}


def gravar_cloud(credencial, modelo, fala):
    """Google Cloud Text-to-Speech: a direção vai no campo "prompt" e só o texto é falado."""
    corpo = {
        "input": {"prompt": direcao_de_voz([fala]), "text": fala.falado},
        "voice": {"languageCode": "pt-BR", "name": VOZ_GEMINI, "modelName": modelo},
        "audioConfig": {"audioEncoding": "LINEAR16", "sampleRateHertz": 24000},
    }
    try:
        try:
            resposta = chamar(URL_CLOUD, credencial.cabecalhos(), corpo)
        except RuntimeError as erro:
            if "HTTP 401" not in str(erro):
                raise
            resposta = chamar(URL_CLOUD, credencial.cabecalhos(renovar=True), corpo)  # token vencido
    except RuntimeError as erro:
        texto = str(erro)
        if "SERVICE_DISABLED" in texto or "has not been used" in texto:
            # As vozes do Gemini precisam de duas APIs: Cloud Text-to-Speech e Agent Platform (antigo Vertex AI).
            servico = re.search(r"([A-Za-z -]+API) has not been used", texto)
            nome = servico.group(1).strip() if servico else "Cloud Text-to-Speech"
            raise ErroDeConfiguracao(f"a {nome} não está ativada neste projeto do Google Cloud") from erro
        if "BILLING" in texto.upper():
            raise ErroDeConfiguracao("o projeto do Google Cloud está sem faturamento ativo") from erro
        if "HTTP 401" in texto or "HTTP 403" in texto:
            raise ErroDeConfiguracao(f"o Google Cloud recusou o acesso ({texto[:200]})") from erro
        raise
    audio = base64.b64decode(resposta.get("audioContent", ""))
    if not audio:
        raise RuntimeError("o Google Cloud não devolveu áudio")
    with wave.open(io.BytesIO(audio), "rb") as arquivo:
        return arquivo.readframes(arquivo.getnframes()), arquivo.getframerate(), modelo


def gravar_vertex(credencial, modelo, falas):
    """Agent Platform (Vertex AI): o mesmo pedido do AI Studio, com o login do Google Cloud."""
    corpo = {
        "contents": [{"role": "user", "parts": [{"text": roteiro(falas)}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": VOZ_GEMINI}}},
        },
    }
    url = URL_VERTEX.format(projeto=credencial.projeto, modelo=modelo)
    for tentativa in range(2):  # às vezes a resposta vem sem áudio; na segunda vez costuma vir
        try:
            try:
                resposta = chamar(url, credencial.cabecalhos(), corpo)
            except RuntimeError as erro:
                if "HTTP 401" not in str(erro):
                    raise
                resposta = chamar(url, credencial.cabecalhos(renovar=True), corpo)  # token vencido
        except RuntimeError as erro:
            texto = str(erro)
            if "SERVICE_DISABLED" in texto or "has not been used" in texto:
                raise ErroDeConfiguracao("a Agent Platform API (Vertex AI) não está ativada no projeto") from erro
            if "BILLING" in texto.upper():
                raise ErroDeConfiguracao("o projeto do Google Cloud está sem faturamento ativo") from erro
            if "HTTP 403" in texto or "HTTP 404" in texto:
                raise ErroDeConfiguracao(f"o Google Cloud recusou o pedido ({texto[:200]})") from erro
            raise
        candidato = resposta.get("candidates", [{}])[0]
        partes = candidato.get("content", {}).get("parts", [])
        dados = next((p["inlineData"] for p in partes if "inlineData" in p), None)
        if dados is None:
            print(f"   o modelo não devolveu áudio ({candidato.get('finishReason')}); tentando de novo")
            continue
        taxa = int((re.search(r"rate=(\d+)", dados.get("mimeType", "")) or [None, "24000"])[1])
        return base64.b64decode(dados["data"]), taxa, modelo
    raise RuntimeError("o modelo não devolveu áudio duas vezes seguidas")


def gravar_gemini(chave_api, modelos, falas):
    """Um pedido ao Gemini com uma ou mais falas; devolve o áudio (PCM 16 bits), a taxa e o modelo usado."""
    corpo = {
        "contents": [{"role": "user", "parts": [{"text": roteiro(falas)}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": VOZ_GEMINI}}},
        },
    }
    ultimo_erro = None
    for modelo in modelos:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent"
        for tentativa in range(2):  # às vezes a resposta vem sem áudio; na segunda vez costuma vir
            try:
                resposta = chamar(url, {"x-goog-api-key": chave_api, "Content-Type": "application/json"}, corpo)
            except RuntimeError as erro:
                ultimo_erro = erro
                if re.search(r"HTTP (400|403|404)", str(erro)):
                    break  # modelo indisponível para esta chave: tenta o próximo
                raise
            candidato = resposta.get("candidates", [{}])[0]
            partes = candidato.get("content", {}).get("parts", [])
            dados = next((p["inlineData"] for p in partes if "inlineData" in p), None)
            if dados is None:
                ultimo_erro = RuntimeError(f"o modelo {modelo} não devolveu áudio ({candidato.get('finishReason')})")
                print(f"   {ultimo_erro}; tentando de novo")
                continue
            taxa = int((re.search(r"rate=(\d+)", dados.get("mimeType", "")) or [None, "24000"])[1])
            return base64.b64decode(dados["data"]), taxa, modelo
        else:
            raise ultimo_erro
    raise RuntimeError(f"nenhum modelo de voz do Gemini funcionou: {ultimo_erro}")


def lotes(falas, tamanho):
    """Agrupa as falas do mesmo tipo em lotes (um pedido por lote)."""
    fila = []
    for tipo in TIPOS:
        do_tipo = [f for f in falas if f.tipo == tipo]
        for i in range(0, len(do_tipo), tamanho):
            fila.append(do_tipo[i:i + tamanho])
    return fila


# =====================================================================
# PÁGINA DE REVISÃO
# =====================================================================


def criar_pagina_de_revisao(falas, indice, suspeitas):
    """ferramentas/revisao_audios.html: todas as falas gravadas, com os áudios dentro (abre em qualquer navegador)."""
    linhas = []
    for tipo in TIPOS:
        itens = [f for f in falas if f.tipo == tipo and existe(f, indice)]
        if not itens:
            continue
        linhas.append(f"<h2>{PASTAS_DOS_TIPOS[tipo].capitalize()} ({len(itens)})</h2><ul>")
        for fala in itens:
            arquivo = indice["clips"][fala.chave]
            with open(os.path.join(PASTA_VOZ, arquivo), "rb") as audio:
                tipo_mime = "audio/ogg" if arquivo.endswith(".ogg") else "audio/wav"
                dados = f"data:{tipo_mime};base64," + base64.b64encode(audio.read()).decode("ascii")
            marca = ' class="suspeita"' if fala.chave in suspeitas else ""
            linhas.append(
                f'<li{marca}><button onclick="tocar(this)" data-src="{dados}" aria-label="Ouvir">&#9654;</button> '
                f"<b>{html.escape(fala.texto)}</b> <small>diz: {html.escape(fala.falado)}</small></li>"
            )
        linhas.append("</ul>")
    pagina = f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><title>Revisão dos áudios</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root {{ --bg: #fbf8f3; --ink: #2b2620; --muted: #7a6f62; --line: #eee4d6; --accent: #b4562d; --warn: #fff1c9; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #1c1916; --ink: #f1ebe3; --muted: #b3a898; --line: #3a342d; --accent: #e0875c; --warn: #4a3f1c; }} }}
body {{ font: 16px/1.5 system-ui, sans-serif; margin: 0 auto; max-width: 820px; padding: 20px 16px; background: var(--bg); color: var(--ink); }}
ul {{ list-style: none; padding: 0; }} li {{ padding: 4px 0; border-bottom: 1px solid var(--line); }}
button {{ border: 0; border-radius: 999px; width: 34px; height: 34px; background: var(--accent); color: #fff; cursor: pointer; margin-right: 8px; }}
small {{ color: var(--muted); }} .suspeita {{ background: var(--warn); }}
</style></head><body>
<h1>Revisão dos áudios da trilha</h1>
<p>Voz: {html.escape(indice.get("voz", ""))} ({html.escape(indice.get("modelo", ""))}). As linhas em amarelo ficaram mais
longas que o esperado: ouça com atenção. Para regravar uma fala: <code>python gerar_audios.py --so "texto" --refazer</code>.</p>
{"".join(linhas)}
<script>
let atual = null;
function tocar(botao) {{ if (atual) atual.pause(); atual = new Audio(botao.dataset.src); atual.play(); }}
</script></body></html>
"""
    with open(PAGINA_REVISAO, "w", encoding="utf-8") as arquivo:
        arquivo.write(pagina)


# =====================================================================
# PROGRAMA
# =====================================================================


def ler_propriedades():
    """Lê as chaves do local.properties (linhas no formato nome=valor)."""
    valores = {}
    if not os.path.exists(ARQUIVO_PROPRIEDADES):
        return valores
    with open(ARQUIVO_PROPRIEDADES, encoding="utf-8") as arquivo:
        for linha in arquivo:
            linha = linha.strip()
            if linha and not linha.startswith("#") and "=" in linha:
                nome, valor = linha.split("=", 1)
                valores[nome.strip()] = valor.strip()
    return valores


def filtrar(falas, opcoes):
    if opcoes.tipo:
        tipos = [t.strip() for t in opcoes.tipo.split(",")]
        desconhecidos = [t for t in tipos if t not in TIPOS]
        if desconhecidos:
            raise SystemExit(f"Tipos válidos: {', '.join(TIPOS)}")
        falas = [f for f in falas if f.tipo in tipos]
    if opcoes.so:
        pedidas = [p.strip() for p in opcoes.so.split(",") if p.strip()]
        escolhidas = [f for f in falas if any(p == f.chave or normalizar(p).upper() == f.chave.split(":", 1)[1].upper() for p in pedidas)]
        if not escolhidas:
            raise SystemExit("Nenhuma fala encontrada com esse texto.")
        falas = escolhidas
    return falas


def salvar_fala(fala, pcm, taxa, modelo, indice, extensao, suspeitas):
    """Apara, nivela, grava o arquivo e atualiza o índice. Devolve a duração em segundos."""
    pcm, segundos = aparar_e_nivelar(pcm, taxa)
    if segundos == 0:
        raise RuntimeError("áudio em silêncio")
    arquivo = nome_do_arquivo(fala, indice, extensao)
    antigo = indice["clips"].get(fala.chave)
    gravar_arquivo(pcm, taxa, os.path.join(PASTA_VOZ, arquivo))
    if antigo and antigo != arquivo and os.path.exists(os.path.join(PASTA_VOZ, antigo)):
        os.remove(os.path.join(PASTA_VOZ, antigo))
    indice["clips"][fala.chave] = arquivo
    indice["modelo"] = modelo
    salvar_indice(indice)
    if segundos > 2.2 * duracao_esperada(fala.falado) + 1.0:
        suspeitas.add(fala.chave)
    return segundos


def main():
    for fluxo in (sys.stdout, sys.stderr):
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Grava as falas fixas da trilha com a voz do Gemini.")
    parser.add_argument("--teste", action="store_true", help="só mostra o que falta gravar")
    parser.add_argument("--tipo", default="", help="só estes tipos: fala, letra, silaba, palavra, frase")
    parser.add_argument("--so", default="", help='só estas falas, separadas por vírgula (ex.: BA,BOLA ou "silaba:BA")')
    parser.add_argument("--refazer", action="store_true", help="grava de novo o que já existe")
    parser.add_argument("--provedor", choices=["vertex", "cloud", "gemini"], default="vertex",
                        help="vertex = Agent Platform do Google Cloud (padrão, pode ir para a loja); "
                             "cloud = Cloud Text-to-Speech; gemini = AI Studio (só testes: os termos vetam apps infantis)")
    parser.add_argument("--modelo", help=f"modelo de voz (padrão: {MODELO_VERTEX} na Agent Platform, "
                                         f"{MODELO_CLOUD} no Cloud Text-to-Speech)")
    parser.add_argument("--lote", type=int, default=1,
                        help="falas por pedido (padrão 1). Mais de 1 economiza pedidos no plano gratuito, mas o modelo "
                             "às vezes para no meio do lote; o script confere e regrava, e você precisa ouvir tudo")
    opcoes = parser.parse_args()

    todas = ler_falas()
    falas = filtrar(todas, opcoes)
    indice = ler_indice()
    faltando = [f for f in falas if opcoes.refazer or not existe(f, indice)]
    # O Google Cloud aceita a direção de voz num campo separado; lá cada fala vai num pedido.
    fila = lotes(faltando, 1 if opcoes.provedor in ("cloud", "vertex") else max(1, opcoes.lote))
    modelo_cloud = opcoes.modelo or (MODELO_VERTEX if opcoes.provedor == "vertex" else MODELO_CLOUD)

    contagem = {t: sum(1 for f in todas if f.tipo == t) for t in TIPOS}
    print("Falas da trilha: " + ", ".join(f"{contagem[t]} {PASTAS_DOS_TIPOS[t]}" for t in TIPOS) + ".")
    minutos = sum(duracao_esperada(f.falado) for f in faltando) / 60
    print(f"A gravar agora: {len(faltando)} (cerca de {minutos:.0f} minutos de áudio, em {len(fila)} pedidos).")
    if opcoes.provedor in ("cloud", "vertex"):
        custo = minutos * DOLARES_POR_MINUTO.get(modelo_cloud, 0.03)
        print(f"Google Cloud, modelo {modelo_cloud}: custo estimado de US$ {custo:.2f}.")
    print()
    if opcoes.teste:
        for fala in faltando:
            print(f"  {fala.chave:<52} -> diz \"{fala.falado}\"")
        return 0
    if not faltando:
        criar_pagina_de_revisao(todas, indice, set())
        print("Nada a gravar. Página de revisão: ferramentas/revisao_audios.html")
        return 0

    propriedades = ler_propriedades()
    credencial, chave_api = None, ""
    if opcoes.provedor in ("cloud", "vertex"):
        try:
            credencial = CredencialCloud(propriedades.get("googlecloud.projeto", ""))
        except ErroDeConfiguracao as erro:
            print(f"Google Cloud: {erro}.")
            return 1
        print(f"Google Cloud: projeto {credencial.projeto}.\n")
    else:
        chave_api = propriedades.get("gemini.apiKey", "")
        if not chave_api:
            print("Chave não encontrada. Coloque no local.properties (na raiz do projeto):")
            print("    gemini.apiKey=AIza...")
            return 1
    if indice.get("voz") != VOZ_GEMINI and indice["clips"] and not opcoes.refazer:
        print(f"Os áudios existentes são da voz {indice.get('voz')}. Para trocar para {VOZ_GEMINI}, use --refazer.")
        return 1
    indice["voz"] = VOZ_GEMINI
    indice["provedor"] = {"vertex": "Google Cloud Agent Platform (Vertex AI)", "cloud": "Google Cloud Text-to-Speech",
                          "gemini": "Gemini API (AI Studio)"}[opcoes.provedor]

    extensao = ".ogg" if shutil.which("ffmpeg") else ".wav"
    if extensao == ".wav":
        print("Aviso: ffmpeg não encontrado; os áudios vão sair em .wav (bem maiores).\n")
    modelos = [opcoes.modelo] if opcoes.modelo else MODELOS_GEMINI

    gravadas, falhas, suspeitas, pedidos = 0, [], set(), 0
    try:
        while fila:
            lote = fila.pop(0)
            rotulo = lote[0].chave if len(lote) == 1 else f"{len(lote)} {PASTAS_DOS_TIPOS[lote[0].tipo]} ({lote[0].texto} ... {lote[-1].texto})"
            try:
                if opcoes.provedor == "vertex":
                    pcm, taxa, modelo = gravar_vertex(credencial, modelo_cloud, lote)
                elif opcoes.provedor == "cloud":
                    pcm, taxa, modelo = gravar_cloud(credencial, modelo_cloud, lote[0])
                else:
                    pcm, taxa, modelo = gravar_gemini(chave_api, modelos, lote)
                pedidos += 1
                pedacos = separar(pcm, taxa, [duracao_esperada(f.falado) for f in lote])
                if pedacos is None:
                    if len(lote) == 1:
                        raise RuntimeError("o áudio veio vazio ou estranho")
                    meio = len(lote) // 2
                    print(f"  não deu para separar {rotulo}; gravando em duas metades")
                    fila[0:0] = [lote[:meio], lote[meio:]]
                    continue
                for fala, pedaco in zip(lote, pedacos):
                    segundos = salvar_fala(fala, pedaco, taxa, modelo, indice, extensao, suspeitas)
                    gravadas += 1
                    aviso = "  <- mais longa que o esperado, ouça" if fala.chave in suspeitas else ""
                    print(f"  {fala.chave} ({segundos:.1f} s){aviso}")
            except (LimiteDoDia, ErroDeConfiguracao):
                raise
            except Exception as erro:  # segue para os próximos e mostra tudo no fim
                falhas.append(f"{rotulo}: {erro}")
                print(f"  {rotulo} FALHOU: {erro}")
            time.sleep(0.5)
    except LimiteDoDia as limite:
        print(f"\nO limite do dia do Gemini acabou ({limite}).")
        print("O que foi gravado está salvo; rode de novo amanhã para continuar.")
    except ErroDeConfiguracao as erro:
        print(f"\nParei: {erro}. O que foi gravado está salvo.")

    criar_pagina_de_revisao(todas, indice, suspeitas)
    restantes = sum(1 for f in todas if not existe(f, indice))
    print(f"\nPedidos: {pedidos}. Gravadas agora: {gravadas}. Falhas: {len(falhas)}. Ainda sem áudio: {restantes}.")
    for falha in falhas:
        print("  " + falha)
    print("Ouça tudo em ferramentas/revisao_audios.html antes de fazer o commit.")
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
