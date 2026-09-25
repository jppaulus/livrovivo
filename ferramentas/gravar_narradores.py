"""
Livro Vivo - Amostras dos narradores
====================================

Grava a saudação de cada narrador ("Oi! Eu sou o Capitão Aventura...") com a voz da Azure dele e guarda em
app/src/main/assets/voz/narradores/<id>.ogg. É o que os pais ouvem ao escolher o narrador: toca na hora,
sem internet. As vozes vêm do app (VoicePersona.kt), então trocar a voz lá e rodar de novo basta.

Como usar (mesma configuração da Azure do gerar_audios.py):
    python gravar_narradores.py
"""

import os
import re
import sys

import gerar_audios as g

ARQUIVO_PERSONAS = os.path.join(g.RAIZ, "app", "src", "main", "java", "com", "livrovivo", "app", "core", "audio",
                                "VoicePersona.kt")
PASTA = os.path.join(g.PASTA_VOZ, "narradores")
# Os narradores com artigo masculino; os outros usam "a".
MASCULINOS = {"ursinho", "aventureiro"}


def ler_personas():
    """(id, título, voz da Azure) de cada narrador, lidos do VoicePersona.kt."""
    texto = open(ARQUIVO_PERSONAS, encoding="utf-8").read()
    personas = re.findall(r'id = "([^"]+)",\s*title = "([^"]+)",.*?azureVoice = "([^"]+)"', texto, flags=re.S)
    if len(personas) < 4:
        raise SystemExit("Não achei os narradores no VoicePersona.kt")
    return personas


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    props = g.ler_propriedades()
    chave, regiao = props.get("azure.speechKey", ""), props.get("azure.speechRegion", "")
    if not chave or not regiao:
        print("Coloque azure.speechKey e azure.speechRegion no local.properties.")
        return 1
    for pid, titulo, voz in ler_personas():
        artigo = "o" if pid in MASCULINOS else "a"
        texto = f"Oi! Eu sou {artigo} {titulo}. Hoje vamos viver uma história mágica juntos, cheia de estrelas e surpresas!"
        fala = g.Fala("frase", texto, texto)
        pcm, taxa, _ = g.gravar_azure_conferido(chave, regiao, voz, fala)
        pcm, segundos = g.aparar_e_nivelar(pcm, taxa)
        g.gravar_arquivo(pcm, taxa, os.path.join(PASTA, f"{pid}.ogg"))
        print(f"  {pid:<12} {voz:<38} {segundos:.1f} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
