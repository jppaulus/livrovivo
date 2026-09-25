"""
Livro Vivo - Amostras de vozes (Microsoft Azure)
================================================

Grava o mesmo trecho de história e as mesmas falas da trilha em todas as vozes em
português do Brasil da Azure, para escolher de ouvido a voz de cada narrador
(Capitão, Ursinho, Vovó, Fada) e a da trilha.

Por que Azure: os termos do Google Cloud proíbem IA generativa (Gemini, inclusive as
vozes) em apps usados por menores de 18 anos; os da Microsoft não têm essa proibição
e pedem um aviso claro aos pais de que a voz é sintética. Só o plano pago (S0) dá
direito de usar o áudio gerado no app.

Como usar:
    1. No local.properties (raiz do projeto, fora do git):
           azure.speechKey=...        (chave do recurso "Speech" na Azure)
           azure.speechRegion=eastus  (região do recurso)
    2. Na pasta deste arquivo:
           python amostras_vozes.py                 (todas as vozes pt-BR)
           python amostras_vozes.py --teste         (só lista as vozes e o custo)
           python amostras_vozes.py --vozes pt-BR-FranciscaNeural,pt-BR-AntonioNeural
    3. Abra ferramentas/amostras_vozes.html no navegador e ouça.
"""

import argparse
import base64
import html
import json
import os
import sys
import time
import urllib.error
import urllib.request
from xml.sax.saxutils import escape

PASTA = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(PASTA)
ARQUIVO_PROPRIEDADES = os.path.join(RAIZ, "local.properties")
PAGINA = os.path.join(PASTA, "amostras_vozes.html")

# Preço de tabela mais alto das vozes (HD): US$ 30 por milhão de caracteres. As neurais comuns custam metade.
DOLARES_POR_CARACTERE = 30 / 1_000_000

HISTORIA = (
    "Era uma vez uma coelhinha chamada Lia, que morava perto de um lago cheio de vaga-lumes. "
    "Toda noite, antes de dormir, ela contava as estrelas pela janela. "
    "Uma, duas, três... sussurrava, bem baixinho. "
    "Mas naquela noite, uma estrela piscou de volta! "
    "Lia arregalou os olhos: Oi, estrelinha! Você também não consegue dormir?"
)
TRILHA = ["Toque na sílaba bá.", "Muito bem!", "Tente de novo!", "Toque na letra F.", "Incrível! Você ganhou 3 estrelas!"]


def ler_propriedades():
    valores = {}
    if os.path.exists(ARQUIVO_PROPRIEDADES):
        with open(ARQUIVO_PROPRIEDADES, encoding="utf-8") as arquivo:
            for linha in arquivo:
                linha = linha.strip()
                if linha and not linha.startswith("#") and "=" in linha:
                    nome, valor = linha.split("=", 1)
                    valores[nome.strip()] = valor.strip()
    return valores


class Azure:
    def __init__(self, chave, regiao):
        self.chave = chave
        self.base = f"https://{regiao}.tts.speech.microsoft.com/cognitiveservices"

    def _pedir(self, url, corpo=None, cabecalhos=None):
        for tentativa in range(5):
            pedido = urllib.request.Request(url, data=corpo, method="POST" if corpo else "GET", headers={
                "Ocp-Apim-Subscription-Key": self.chave, "User-Agent": "livro-vivo-amostras", **(cabecalhos or {})})
            try:
                with urllib.request.urlopen(pedido, timeout=120) as resposta:
                    return resposta.read()
            except urllib.error.HTTPError as erro:
                detalhe = erro.read().decode("utf-8", "replace")[:300]
                if erro.code == 429 or erro.code >= 500:
                    time.sleep(3 * (tentativa + 1))
                    continue
                if erro.code in (401, 403):
                    raise SystemExit("A Azure recusou a chave: confira azure.speechKey e azure.speechRegion "
                                     "no local.properties (a região é a do recurso, ex.: eastus).") from erro
                raise RuntimeError(f"HTTP {erro.code}: {detalhe}") from erro
            except (urllib.error.URLError, TimeoutError) as erro:
                if tentativa < 2:
                    time.sleep(3)
                    continue
                raise RuntimeError(f"sem conexão: {getattr(erro, 'reason', erro)}") from erro
        raise RuntimeError("a Azure continuou pedindo pausa")

    def vozes(self):
        return json.loads(self._pedir(f"{self.base}/voices/list"))

    def falar(self, ssml, formato="audio-24khz-48kbitrate-mono-mp3"):
        return self._pedir(f"{self.base}/v1", ssml.encode("utf-8"), {
            "Content-Type": "application/ssml+xml", "X-Microsoft-OutputFormat": formato})


def eh_hd(voz):
    return "DragonHD" in voz["ShortName"]


def ssml(voz, trechos, velocidade=None):
    """Um pedido SSML com as falas separadas por pausas."""
    pausa = '<break time="900ms"/>'
    corpo = pausa.join(escape(t) for t in trechos)
    if velocidade:
        corpo = f'<prosody rate="{velocidade}">{corpo}</prosody>'
    return ('<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" '
            'xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="pt-BR">'
            f'<voice name="{voz["ShortName"]}">{corpo}</voice></speak>')


def falar(azure, voz, trechos, velocidade):
    """Algumas vozes novas (prévia) recusam mudar a velocidade: nesse caso, grava na velocidade normal."""
    try:
        return azure.falar(ssml(voz, trechos, velocidade))
    except RuntimeError as erro:
        if "HTTP 400" not in str(erro):
            raise
        return azure.falar(ssml(voz, trechos))


def descricao(voz):
    marcas = []
    if eh_hd(voz):
        marcas.append("HD")
    if "Multilingual" in voz["ShortName"]:
        marcas.append("multilíngue")
    if voz.get("Status", "GA") != "GA":
        marcas.append("prévia")
    estilos = voz.get("StyleList") or []
    if estilos:
        marcas.append("estilos: " + ", ".join(estilos[:6]))
    return " · ".join(marcas)


def criar_pagina(amostras, regiao):
    grupos = {"Male": "Vozes masculinas", "Female": "Vozes femininas"}
    partes = []
    for genero, titulo in grupos.items():
        itens = [a for a in amostras if a["voz"]["Gender"] == genero]
        if not itens:
            continue
        partes.append(f"<h2>{titulo} ({len(itens)})</h2><ul>")
        for a in itens:
            voz = a["voz"]
            botoes = "".join(
                f'<button onclick="tocar(this)" data-src="data:audio/mpeg;base64,{base64.b64encode(audio).decode()}">'
                f"&#9654; {rotulo}</button>"
                for rotulo, audio in (("História", a["historia"]), ("Trilha", a["trilha"])) if audio
            )
            erro = f'<small class="erro">falhou: {html.escape(a["erro"])}</small>' if a.get("erro") else ""
            partes.append(
                f'<li><div><b>{html.escape(voz.get("LocalName") or voz["DisplayName"])}</b> '
                f'<code>{html.escape(voz["ShortName"])}</code><br><small>{html.escape(descricao(voz))}</small>{erro}</div>'
                f"<div class=\"botoes\">{botoes}</div></li>"
            )
        partes.append("</ul>")
    pagina = f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><title>Amostras de vozes</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root {{ --bg: #fbf8f3; --ink: #2b2620; --muted: #7a6f62; --line: #eee4d6; --accent: #b4562d; --warn: #b3261e; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg: #1c1916; --ink: #f1ebe3; --muted: #b3a898; --line: #3a342d; --accent: #e0875c; --warn: #ff8a80; }} }}
body {{ font: 16px/1.5 system-ui, sans-serif; margin: 0 auto; max-width: 860px; padding: 20px 16px; background: var(--bg); color: var(--ink); }}
ul {{ list-style: none; padding: 0; }}
li {{ display: flex; flex-wrap: wrap; gap: 8px 16px; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--line); }}
code {{ font-size: 13px; color: var(--muted); overflow-wrap: anywhere; }} small {{ color: var(--muted); }} .erro {{ color: var(--warn); display: block; }}
.botoes {{ display: flex; gap: 8px; }}
button {{ border: 0; border-radius: 999px; padding: 8px 14px; background: var(--accent); color: #fff; cursor: pointer; font: inherit; }}
blockquote {{ margin: 0; padding: 8px 12px; border-left: 3px solid var(--line); color: var(--muted); }}
</style></head><body>
<h1>Amostras de vozes (Microsoft Azure)</h1>
<p>Todas as vozes em português do Brasil disponíveis na região <b>{html.escape(regiao)}</b>. Anote o nome
(o código cinza) das que servirem para cada narrador: <b>Capitão Aventura</b>, <b>Ursinho Gentil</b>,
<b>Vovó Contadora</b>, <b>Fada Encantada</b> e a <b>voz da trilha</b> (hoje é o Capitão). A história sai um pouco
mais devagar que o normal; isso se ajusta depois.</p>
<blockquote><b>História:</b> {html.escape(HISTORIA)}<br><b>Trilha:</b> {html.escape(" / ".join(TRILHA))}</blockquote>
{"".join(partes)}
<script>
let atual = null;
function tocar(botao) {{ if (atual) atual.pause(); atual = new Audio(botao.dataset.src); atual.play(); }}
</script></body></html>
"""
    with open(PAGINA, "w", encoding="utf-8") as arquivo:
        arquivo.write(pagina)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Amostras das vozes pt-BR da Azure para escolher os narradores.")
    parser.add_argument("--teste", action="store_true", help="só lista as vozes e o custo, sem gravar")
    parser.add_argument("--vozes", default="", help="só estas vozes (códigos separados por vírgula)")
    opcoes = parser.parse_args()

    propriedades = ler_propriedades()
    chave, regiao = propriedades.get("azure.speechKey", ""), propriedades.get("azure.speechRegion", "")
    if not chave or not regiao:
        print("Coloque no local.properties (na raiz do projeto):")
        print("    azure.speechKey=...")
        print("    azure.speechRegion=eastus")
        return 1
    azure = Azure(chave, regiao)
    vozes = [v for v in azure.vozes() if v.get("Locale") == "pt-BR"]
    if opcoes.vozes:
        pedidas = {v.strip() for v in opcoes.vozes.split(",")}
        vozes = [v for v in vozes if v["ShortName"] in pedidas]
    vozes.sort(key=lambda v: (v["Gender"], not eh_hd(v), v["ShortName"]))
    caracteres = len(vozes) * (len(HISTORIA) + sum(len(t) for t in TRILHA))
    print(f"{len(vozes)} vozes pt-BR em {regiao}; {caracteres} caracteres, no máximo US$ {caracteres * DOLARES_POR_CARACTERE:.2f}.")
    for voz in vozes:
        print(f"  {voz['Gender']:<6} {voz['ShortName']:<45} {descricao(voz)}")
    if opcoes.teste:
        return 0

    amostras = []
    for voz in vozes:
        amostra = {"voz": voz, "historia": None, "trilha": None}
        try:
            amostra["historia"] = falar(azure, voz, [HISTORIA], "-8%")
            amostra["trilha"] = falar(azure, voz, TRILHA, "-5%")
            print(f"  ok  {voz['ShortName']}")
        except RuntimeError as erro:
            amostra["erro"] = str(erro)
            print(f"  FALHOU {voz['ShortName']}: {erro}")
        amostras.append(amostra)
    criar_pagina(amostras, regiao)
    print(f"\nOuça em {os.path.relpath(PAGINA, RAIZ)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
