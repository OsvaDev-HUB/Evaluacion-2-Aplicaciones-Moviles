# Convierte las ilustraciones SVG de esta carpeta a VectorDrawable de Android.
#   claro/ill_x.svg  -> app/src/main/res/drawable/ill_x.xml
#   oscuro/ill_x.svg -> app/src/main/res/drawable/ill_x_dark.xml
# No se usa drawable-night porque la app permite elegir el tema en Perfil y drawable-night
# solo sigue el tema del sistema. La version la elige el composable Illustration en Compose.
# Uso (desde la raiz del proyecto): python3 docs/ilustraciones/convertir.py
# Solo soporta lo que usan estas ilustraciones: rect (con rx), circle y path,
# con relleno, opacidad del relleno y trazo.
import pathlib
import xml.etree.ElementTree as ET

RAIZ = pathlib.Path(__file__).resolve().parents[2]
DESTINO = RAIZ / "app/src/main/res/drawable"
SUFIJOS = {"claro": "", "oscuro": "_dark"}
SVG = "{http://www.w3.org/2000/svg}"
TAPAS = {"round": "round", "square": "square", "butt": "butt"}
UNIONES = {"round": "round", "bevel": "bevel", "miter": "miter"}


def num(valor):
    # Quita los decimales que sobran para que el XML quede corto y legible.
    n = float(valor)
    return str(int(n)) if n == int(n) else f"{n:g}"


def rect_a_path(e):
    x, y = float(e.get("x", 0)), float(e.get("y", 0))
    w, h = float(e.get("width")), float(e.get("height"))
    r = min(float(e.get("rx", e.get("ry", 0))), w / 2, h / 2)
    if r == 0:
        return f"M{num(x)},{num(y)}h{num(w)}v{num(h)}h{num(-w)}z"
    return (
        f"M{num(x + r)},{num(y)}h{num(w - 2 * r)}"
        f"a{num(r)},{num(r)} 0,0 1,{num(r)},{num(r)}v{num(h - 2 * r)}"
        f"a{num(r)},{num(r)} 0,0 1,{num(-r)},{num(r)}h{num(-(w - 2 * r))}"
        f"a{num(r)},{num(r)} 0,0 1,{num(-r)},{num(-r)}v{num(-(h - 2 * r))}"
        f"a{num(r)},{num(r)} 0,0 1,{num(r)},{num(-r)}z"
    )


def circle_a_path(e):
    cx, cy, r = float(e.get("cx")), float(e.get("cy")), float(e.get("r"))
    return (
        f"M{num(cx - r)},{num(cy)}a{num(r)},{num(r)} 0,1 1,{num(2 * r)},0"
        f"a{num(r)},{num(r)} 0,1 1,{num(-2 * r)},0z"
    )


def color(valor):
    return valor.upper() if valor and valor.startswith("#") else None


def convertir(svg, destino):
    raiz = ET.parse(svg).getroot()
    _, _, vw, vh = raiz.get("viewBox").split()
    lineas = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Generado desde docs/ilustraciones/{svg.parent.name}/{svg.name} con convertir.py -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{num(vw)}dp"',
        f'    android:height="{num(vh)}dp"',
        f'    android:viewportWidth="{num(vw)}"',
        f'    android:viewportHeight="{num(vh)}">',
    ]
    for e in raiz.iter():
        etiqueta = e.tag.replace(SVG, "")
        if etiqueta == "rect":
            datos = rect_a_path(e)
        elif etiqueta == "circle":
            datos = circle_a_path(e)
        elif etiqueta == "path":
            datos = " ".join(e.get("d").split())
        else:
            continue
        atributos = [f'android:pathData="{datos}"']
        relleno = color(e.get("fill", "#000000"))
        if relleno:
            atributos.append(f'android:fillColor="{relleno}"')
            if e.get("fill-opacity"):
                atributos.append(f'android:fillAlpha="{num(e.get("fill-opacity"))}"')
        trazo = color(e.get("stroke"))
        if trazo:
            atributos.append(f'android:strokeColor="{trazo}"')
            atributos.append(f'android:strokeWidth="{num(e.get("stroke-width", 1))}"')
            if e.get("stroke-linecap") in TAPAS:
                atributos.append(f'android:strokeLineCap="{TAPAS[e.get("stroke-linecap")]}"')
            if e.get("stroke-linejoin") in UNIONES:
                atributos.append(f'android:strokeLineJoin="{UNIONES[e.get("stroke-linejoin")]}"')
        lineas.append("    <path\n        " + "\n        ".join(atributos) + " />")
    lineas.append("</vector>")
    destino.write_text("\n".join(lineas) + "\n", encoding="utf-8")


for carpeta, sufijo in SUFIJOS.items():
    for svg in sorted((pathlib.Path(__file__).parent / carpeta).glob("*.svg")):
        destino = DESTINO / f"{svg.stem}{sufijo}.xml"
        convertir(svg, destino)
        print(f"{carpeta}/{svg.name} -> {destino.relative_to(RAIZ)}")
