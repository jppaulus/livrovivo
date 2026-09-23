"""
Livro Vivo - Trilha da Leitura - Gerador dos áudios das letras e sílabas
=========================================================================

Grava, uma vez só, a voz do Capitão Aventura falando as 18 letras e as 65
sílabas da trilha. O app toca esses arquivos (res/raw/som_a, som_ba, ...).
Enquanto eles não existem, o app usa a voz do aparelho com uma dica de
pronúncia ("bá, de bala"), então a trilha funciona mesmo sem eles.

Como usar:
    1. Coloque UMA das chaves no arquivo local.properties, na raiz do projeto:
           elevenlabs.apiKey=sk_...
           gemini.apiKey=AIza...
    2. Abra o terminal na pasta deste arquivo e rode:
           python gerar_audios.py --teste      (mostra o que vai ser gravado, sem chamar nada)
           python gerar_audios.py              (grava tudo o que ainda não existe)
           python gerar_audios.py --so BA,BE   (grava só estas letras/sílabas)
           python gerar_audios.py --refazer    (grava de novo os que já existem)
    3. Ouça os arquivos em app/src/main/res/raw/. Se algum ficou estranho,
       grave de novo com --so e --refazer.

Opções:
    --provedor elevenlabs|gemini   Qual serviço usar (o padrão é o que tiver chave).
    --voz ID                       ElevenLabs: id da voz (o padrão escolhe uma voz
                                   masculina e animada da sua conta, como o app faz).
    --modelo NOME                  Modelo de voz (ElevenLabs ou Gemini).

As chaves nunca vão para o repositório (o local.properties está no .gitignore).
O script não precisa de nenhuma biblioteca extra: usa só o que já vem com o Python.
"""

import argparse
import base64
import json
import os
import re
import sys
import time
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
PASTA_AUDIOS = os.path.join(RAIZ, "app", "src", "main", "res", "raw")

# Voz do Capitão Aventura no Gemini (a mesma do app: VoicePersona.AVENTUREIRO).
VOZ_GEMINI = "Puck"
MODELOS_GEMINI = ["gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"]

# Na ElevenLabs, este modelo aceita fixar o idioma (português). Sem isso, um texto
# curtinho como "bê" pode ser lido como se fosse de outra língua.
MODELO_ELEVENLABS = "eleven_turbo_v2_5"

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


# =====================================================================
# FUNÇÕES DE APOIO
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


def ler_unidades():
    """Letras (módulos Vogais e Consoantes) e sílabas (módulo Sílabas), na ordem da trilha."""
    with open(ARQUIVO_TRILHA, encoding="utf-8") as arquivo:
        trilha = json.load(arquivo)
    letras, silabas = [], []
    for modulo in trilha["modulos"]:
        for fase in modulo["fases"]:
            if modulo["id"] in ("vogais", "consoantes"):
                letras += fase["ensina"]
            elif modulo["id"] == "silabas":
                silabas += fase["ensina"]
    return letras, silabas


def nome_do_arquivo(unidade):
    """'BA' -> 'som_ba' (nome do recurso no Android)."""
    return "som_" + unidade.lower()


def arquivo_existente(unidade):
    for extensao in (".mp3", ".wav", ".ogg"):
        caminho = os.path.join(PASTA_AUDIOS, nome_do_arquivo(unidade) + extensao)
        if os.path.exists(caminho):
            return caminho
    return None


def salvar(unidade, dados, extensao):
    """Salva o áudio e apaga outra versão do mesmo nome (o Android não aceita som_ba.mp3 e som_ba.wav juntos)."""
    os.makedirs(PASTA_AUDIOS, exist_ok=True)
    antigo = arquivo_existente(unidade)
    destino = os.path.join(PASTA_AUDIOS, nome_do_arquivo(unidade) + extensao)
    with open(destino, "wb") as arquivo:
        arquivo.write(dados)
    if antigo and antigo != destino:
        os.remove(antigo)
    return destino


def chamar(url, cabecalhos, corpo=None, tentativas=3):
    """Faz a chamada HTTP; espera e tenta de novo quando o serviço pede calma (erro 429)."""
    dados = json.dumps(corpo).encode("utf-8") if corpo is not None else None
    for tentativa in range(tentativas):
        pedido = urllib.request.Request(url, data=dados, headers=cabecalhos, method="POST" if dados else "GET")
        try:
            with urllib.request.urlopen(pedido, timeout=60) as resposta:
                return resposta.read()
        except urllib.error.HTTPError as erro:
            if erro.code == 429 and tentativa < tentativas - 1:
                print("   serviço pediu uma pausa; esperando 20 segundos...")
                time.sleep(20)
                continue
            detalhe = erro.read().decode("utf-8", "replace")[:300]
            raise RuntimeError(f"HTTP {erro.code}: {detalhe}") from erro
    raise RuntimeError("sem resposta")


# =====================================================================
# ELEVENLABS
# =====================================================================


def escolher_voz_elevenlabs(chave):
    """Uma voz masculina, jovem ou adulta, de preferência em português (o mesmo perfil do Capitão no app)."""
    resposta = json.loads(chamar("https://api.elevenlabs.io/v1/voices", {"xi-api-key": chave}))
    vozes = resposta.get("voices", [])
    if not vozes:
        raise RuntimeError("a conta da ElevenLabs não tem vozes")

    def nota(voz):
        rotulos = {k: str(v).lower() for k, v in (voz.get("labels") or {}).items()}
        pontos = 0
        if rotulos.get("gender") == "male":
            pontos += 4
        if rotulos.get("age") in ("young", "middle_aged", "middle aged"):
            pontos += 2
        if "brazil" in rotulos.get("accent", "") or rotulos.get("language", "").startswith("pt"):
            pontos += 3
        return pontos

    melhor = max(vozes, key=nota)
    print(f"Voz da ElevenLabs: {melhor.get('name')} ({melhor['voice_id']}). Para trocar, use --voz.")
    return melhor["voice_id"]


def gravar_elevenlabs(chave, voz, modelo, unidade):
    corpo = {"text": dica(unidade), "model_id": modelo}
    if "v2_5" in modelo:
        corpo["language_code"] = "pt"
    url = f"https://api.elevenlabs.io/v1/text-to-speech/{voz}?output_format=mp3_44100_128"
    audio = chamar(url, {"xi-api-key": chave, "Content-Type": "application/json", "Accept": "audio/mpeg"}, corpo)
    return salvar(unidade, audio, ".mp3")


# =====================================================================
# GEMINI
# =====================================================================


def aparar_silencio(pcm, limite=500, margem=1200):
    """Tira o silêncio do começo e do fim (áudio PCM de 16 bits), deixando uma margem pequena."""
    amostras = [int.from_bytes(pcm[i:i + 2], "little", signed=True) for i in range(0, len(pcm) - 1, 2)]
    fortes = [i for i, a in enumerate(amostras) if abs(a) > limite]
    if not fortes:
        return pcm
    inicio = max(fortes[0] - margem, 0)
    fim = min(fortes[-1] + margem, len(amostras))
    return pcm[inicio * 2:fim * 2]


def gravar_gemini(chave, modelos, unidade, letras):
    tipo = "the letter name" if unidade in letras else "the syllable"
    texto = f"Say in Brazilian Portuguese, clearly and cheerfully, only {tipo}: {dica(unidade)}"
    corpo = {
        "contents": [{"role": "user", "parts": [{"text": texto}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": VOZ_GEMINI}}},
        },
    }
    ultimo_erro = None
    for modelo in modelos:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent"
        try:
            resposta = json.loads(chamar(url, {"x-goog-api-key": chave, "Content-Type": "application/json"}, corpo))
        except RuntimeError as erro:
            ultimo_erro = erro
            if re.search(r"HTTP (400|403|404)", str(erro)):
                continue  # modelo indisponível para esta chave: tenta o próximo
            raise
        partes = resposta["candidates"][0]["content"]["parts"]
        dados = next(p["inlineData"] for p in partes if "inlineData" in p)
        taxa = int((re.search(r"rate=(\d+)", dados.get("mimeType", "")) or [None, "24000"])[1])
        pcm = aparar_silencio(base64.b64decode(dados["data"]))
        caminho_temporario = os.path.join(PASTA_AUDIOS, nome_do_arquivo(unidade) + ".tmp")
        os.makedirs(PASTA_AUDIOS, exist_ok=True)
        with wave.open(caminho_temporario, "wb") as saida:
            saida.setnchannels(1)
            saida.setsampwidth(2)
            saida.setframerate(taxa)
            saida.writeframes(pcm)
        with open(caminho_temporario, "rb") as arquivo:
            conteudo = arquivo.read()
        os.remove(caminho_temporario)
        return salvar(unidade, conteudo, ".wav")
    raise RuntimeError(f"nenhum modelo de voz do Gemini funcionou: {ultimo_erro}")


# =====================================================================
# PROGRAMA
# =====================================================================


def main():
    parser = argparse.ArgumentParser(description="Grava os áudios das letras e sílabas da trilha.")
    parser.add_argument("--teste", action="store_true", help="só mostra o que seria gravado")
    parser.add_argument("--so", default="", help="só estas letras/sílabas, separadas por vírgula (ex.: BA,BE)")
    parser.add_argument("--refazer", action="store_true", help="grava de novo os que já existem")
    parser.add_argument("--provedor", choices=["elevenlabs", "gemini"], help="serviço de voz")
    parser.add_argument("--voz", help="ElevenLabs: id da voz")
    parser.add_argument("--modelo", help="modelo de voz")
    opcoes = parser.parse_args()

    letras, silabas = ler_unidades()
    unidades = letras + silabas
    if opcoes.so:
        pedidas = [u.strip().upper() for u in opcoes.so.split(",") if u.strip()]
        desconhecidas = [u for u in pedidas if u not in unidades]
        if desconhecidas:
            print("Estas não estão na trilha:", ", ".join(desconhecidas))
            return 1
        unidades = pedidas

    print(f"{len(letras)} letras e {len(silabas)} sílabas na trilha; {len(unidades)} para gravar.\n")
    if opcoes.teste:
        for unidade in unidades:
            existente = arquivo_existente(unidade)
            situacao = f"já existe ({os.path.basename(existente)})" if existente else "a gravar"
            print(f"  {unidade:<3} -> fala \"{dica(unidade)}\" -> {nome_do_arquivo(unidade)}  [{situacao}]")
        return 0

    chaves = ler_propriedades()
    chave_eleven = chaves.get("elevenlabs.apiKey", "")
    chave_gemini = chaves.get("gemini.apiKey", "")
    provedor = opcoes.provedor or ("elevenlabs" if chave_eleven else "gemini" if chave_gemini else None)
    if provedor is None or (provedor == "elevenlabs" and not chave_eleven) or (provedor == "gemini" and not chave_gemini):
        print("Nenhuma chave encontrada. Coloque no local.properties (na raiz do projeto):")
        print("    elevenlabs.apiKey=sk_...   ou   gemini.apiKey=AIza...")
        return 1

    voz = None
    if provedor == "elevenlabs":
        voz = opcoes.voz or escolher_voz_elevenlabs(chave_eleven)
        modelo = opcoes.modelo or MODELO_ELEVENLABS
    else:
        modelos = [opcoes.modelo] if opcoes.modelo else MODELOS_GEMINI

    gravados, pulados, falhas = 0, 0, []
    for unidade in unidades:
        if arquivo_existente(unidade) and not opcoes.refazer:
            pulados += 1
            continue
        try:
            if provedor == "elevenlabs":
                caminho = gravar_elevenlabs(chave_eleven, voz, modelo, unidade)
            else:
                caminho = gravar_gemini(chave_gemini, modelos, unidade, letras)
            gravados += 1
            print(f"  {unidade:<3} \"{dica(unidade)}\" -> {os.path.basename(caminho)}")
        except Exception as erro:  # segue para as próximas e mostra tudo no fim
            falhas.append(f"{unidade}: {erro}")
            print(f"  {unidade:<3} FALHOU: {erro}")
        time.sleep(0.4)

    print(f"\nGravados: {gravados}. Já existiam: {pulados}. Falhas: {len(falhas)}.")
    if gravados:
        print("Ouça os arquivos em app/src/main/res/raw/ antes de fazer o commit.")
    return 1 if falhas else 0


if __name__ == "__main__":
    sys.exit(main())
