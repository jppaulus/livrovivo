"""
Livro Vivo - Ícone do app ("livro sorridente", escolhido pelo usuário em 25/09/2026)
===================================================================================

Um desenho só gera tudo:
    app/src/main/res/drawable/ic_launcher_background.xml   fundo (céu com nuvens)
    app/src/main/res/drawable/ic_launcher_foreground.xml   o livro com rostinho e a estrela
    app/src/main/res/drawable/ic_launcher_monochrome.xml   silhueta para os ícones temáticos (Android 13+)
    ferramentas/icone/icone.svg                            o mesmo desenho, para ver no navegador e gerar o PNG da loja

O Android mostra só o miolo de 72 x 72 do quadro de 108 x 108 e recorta em círculo ou quadrado arredondado; o
livro fica numa escala menor (ESCALA) para caber inteiro no círculo seguro de 66.

Como usar:  python gerar_icone.py
"""

import os

PASTA = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(PASTA))
DRAWABLE = os.path.join(RAIZ, "app", "src", "main", "res", "drawable")
ESCALA = 0.8  # frente do ícone, em torno do centro (54, 54)


def retangulo(x, y, w, h, r):
    return (f"M{x + r},{y} h{w - 2 * r} a{r},{r} 0 0 1 {r},{r} v{h - 2 * r} a{r},{r} 0 0 1 {-r},{r} "
            f"h{-(w - 2 * r)} a{r},{r} 0 0 1 {-r},{-r} v{-(h - 2 * r)} a{r},{r} 0 0 1 {r},{-r} z")


def elipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 z"


def circulo(cx, cy, r):
    return elipse(cx, cy, r, r)


# (caminho, preenchimento, opacidade, contorno, largura do contorno)
FUNDO_NUVENS = [
    (elipse(22, 84, 15, 7.5), "#FFFFFF", 0.7),
    (elipse(36, 88, 13, 6.5), "#FFFFFF", 0.7),
    (elipse(86, 82, 14, 7), "#FFFFFF", 0.7),
    (elipse(74, 88, 11, 5.5), "#FFFFFF", 0.7),
]
ESTRELA = "M73,22.5 l2.6,5.4 5.9,0.8 -4.3,4.1 1,5.9 -5.2,-2.8 -5.2,2.8 1,-5.9 -4.3,-4.1 5.9,-0.8 z"
FRENTE = [
    (retangulo(33, 29, 46, 55, 7), "#3E2A7E", 1, None, 0),            # contracapa
    (retangulo(35.5, 31, 42, 51.5, 5), "#FFFDF7", 1, None, 0),        # páginas
    ("M76,36 V78", None, 1, "#E6DCCB", 1),
    ("M73.5,35 V80", None, 1, "#E6DCCB", 1),
    (retangulo(29, 26, 45, 55, 7.5), "#6750A4", 1, None, 0),          # capa
    (retangulo(29, 26, 8, 55, 4), "#7E67C4", 1, None, 0),             # lombada
    ("M62,81 V90 l3.5,-3 3.5,3 V81 z", "#FF8A80", 1, None, 0),        # fitinha
    (elipse(46.5, 49, 5.4, 6.3), "#FFFFFF", 1, None, 0),
    (elipse(61.5, 49, 5.4, 6.3), "#FFFFFF", 1, None, 0),
    (circulo(47.3, 50.2, 3), "#21005D", 1, None, 0),
    (circulo(62.3, 50.2, 3), "#21005D", 1, None, 0),
    (circulo(48.4, 48.9, 1.1), "#FFFFFF", 1, None, 0),
    (circulo(63.4, 48.9, 1.1), "#FFFFFF", 1, None, 0),
    (circulo(41.5, 59, 3.4), "#FF8A80", 0.85, None, 0),              # bochechas
    (circulo(66.5, 59, 3.4), "#FF8A80", 0.85, None, 0),
    ("M47.5,59.5 Q54,67 60.5,59.5 Q54,62.5 47.5,59.5 z", "#21005D", 1, None, 0),   # sorriso
    ("M50.5,62.6 Q54,64.6 57.5,62.6 Q54,65.9 50.5,62.6 z", "#FF8A80", 1, None, 0),
    (ESTRELA, "#FFB74D", 1, "#F29A38", 0.8),
]
# Silhueta: capa com olhos e sorriso vazados (regra par-ímpar) e a estrela.
MONOCROMATICO = [
    (" ".join([retangulo(29, 26, 45, 55, 7.5), elipse(46.5, 49, 5.4, 6.3), elipse(61.5, 49, 5.4, 6.3),
               "M47.5,59.5 Q54,67 60.5,59.5 Q54,62.5 47.5,59.5 z"]), "#FFFFFF", 1, "evenOdd"),
    (ESTRELA, "#FFFFFF", 1, "nonZero"),
]
CEU = ("#B6ECFF", "#63BFF0")


def vetor(conteudo):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Gerado por ferramentas/icone/gerar_icone.py: mude lá e rode de novo. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            f"{conteudo}</vector>\n")


def caminho_xml(d, cor, opacidade, contorno=None, largura=0, recuo="    ", regra=None):
    atributos = [f'android:pathData="{d}"']
    if cor:
        atributos.append(f'android:fillColor="{cor}"')
        if opacidade != 1:
            atributos.append(f'android:fillAlpha="{opacidade}"')
    if contorno:
        atributos += [f'android:strokeColor="{contorno}"', f'android:strokeWidth="{largura}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    if regra:
        atributos.append(f'android:fillType="{regra}"')
    sep = f"\n{recuo}    "
    return f"{recuo}<path{sep}{sep.join(atributos)} />\n"


def fundo_xml():
    ceu = ('    <path android:pathData="M0,0 h108 v108 h-108 z">\n'
           '        <aapt:attr name="android:fillColor">\n'
           '            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54"'
           ' android:endY="108">\n'
           f'                <item android:offset="0" android:color="{CEU[0].replace("#", "#FF")}" />\n'
           f'                <item android:offset="1" android:color="{CEU[1].replace("#", "#FF")}" />\n'
           '            </gradient>\n        </aapt:attr>\n    </path>\n')
    return vetor(ceu + "".join(caminho_xml(d, c, o) for d, c, o in FUNDO_NUVENS))


def grupo(corpo):
    return (f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{ESCALA}" android:scaleY="{ESCALA}">\n'
            f"{corpo}    </group>\n")


def frente_xml():
    return vetor(grupo("".join(caminho_xml(d, c, o, s, w, recuo="        ") for d, c, o, s, w in FRENTE)))


def monocromatico_xml():
    return vetor(grupo("".join(caminho_xml(d, c, o, recuo="        ", regra=r) for d, c, o, r in MONOCROMATICO)))


def svg():
    def p(d, cor, opacidade, contorno=None, largura=0):
        partes = [f'd="{d}"', f'fill="{cor or "none"}"']
        if opacidade != 1:
            partes.append(f'fill-opacity="{opacidade}"')
        if contorno:
            partes.append(f'stroke="{contorno}" stroke-width="{largura}" stroke-linecap="round" stroke-linejoin="round"')
        return f"  <path {' '.join(partes)}/>\n"
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">\n'
            f'  <defs><linearGradient id="ceu" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{CEU[0]}"/>'
            f'<stop offset="1" stop-color="{CEU[1]}"/></linearGradient></defs>\n'
            '  <rect width="108" height="108" fill="url(#ceu)"/>\n'
            + "".join(p(d, c, o) for d, c, o in FUNDO_NUVENS)
            + f'  <g transform="translate(54 54) scale({ESCALA}) translate(-54 -54)">\n'
            + "".join(p(d, c, o, s, w) for d, c, o, s, w in FRENTE)
            + "  </g>\n</svg>\n")


def main():
    os.makedirs(DRAWABLE, exist_ok=True)
    arquivos = {
        os.path.join(DRAWABLE, "ic_launcher_background.xml"): fundo_xml(),
        os.path.join(DRAWABLE, "ic_launcher_foreground.xml"): frente_xml(),
        os.path.join(DRAWABLE, "ic_launcher_monochrome.xml"): monocromatico_xml(),
        os.path.join(PASTA, "icone.svg"): svg(),
    }
    for caminho, texto in arquivos.items():
        with open(caminho, "w", encoding="utf-8", newline="\n") as saida:
            saida.write(texto)
        print("  " + os.path.relpath(caminho, RAIZ))


if __name__ == "__main__":
    main()
