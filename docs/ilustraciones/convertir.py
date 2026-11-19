# Convierte las ilustraciones SVG de esta carpeta a VectorDrawable de Android.
#   claro/ill_x.svg  -> app/src/main/res/drawable/ill_x.xml
#   oscuro/ill_x.svg -> app/src/main/res/drawable/ill_x_dark.xml
# Si la ilustracion tiene animacion (tabla ANIMACIONES), tambien genera
#   avd_ill_x.xml y avd_ill_x_dark.xml (AnimatedVectorDrawable).
# No se usa drawable-night porque la app permite elegir el tema en Perfil y drawable-night
# solo sigue el tema del sistema. La version la elige el composable Illustration en Compose.
# Uso (desde la raiz del proyecto): python3 docs/ilustraciones/convertir.py
# Solo soporta lo que usan estas ilustraciones: rect (con rx), circle, path y g,
# con relleno, opacidad del relleno y trazo.
# Para animar una parte: en el SVG se envuelve en <g id="nombre" data-pivot="x,y">
# (el pivote es el centro para escalar o girar) o se le pone id a un path/rect/circle.
import pathlib
import xml.etree.ElementTree as ET

RAIZ = pathlib.Path(__file__).resolve().parents[2]
DESTINO = RAIZ / "app/src/main/res/drawable"
SUFIJOS = {"claro": "", "oscuro": "_dark"}
SVG = "{http://www.w3.org/2000/svg}"
TAPAS = {"round": "round", "square": "square", "butt": "butt"}
UNIONES = {"round": "round", "bevel": "bevel", "miter": "miter"}
SUAVE = "@android:interpolator/fast_out_slow_in"


# ---------- Animaciones ----------
# Cada animacion va y vuelve a su estado inicial, asi el ciclo se puede repetir sin saltos
# (en la app se reproduce hacia adelante y luego hacia atras).
# Formato: (parte, propiedad, desde, hasta, duracion_ms, retraso_ms)
# Propiedades de grupo: scaleX, scaleY, rotation, translateX, translateY.
# Propiedades de trazo: fillAlpha, strokeAlpha.
def escala(parte, hasta, dur, retraso=0):
    return [(parte, "scaleX", 1, hasta, dur, retraso), (parte, "scaleY", 1, hasta, dur, retraso)]


ANIMACIONES = {
    "ill_sensor_search": [
        ("arco1", "strokeAlpha", 1, 0.25, 350, 0),
        ("arco2", "strokeAlpha", 1, 0.25, 350, 200),
        ("arco3", "strokeAlpha", 1, 0.25, 350, 400),
        ("led", "fillAlpha", 1, 0.3, 300, 500),
    ],
    "ill_email_verify": escala("anillo", 1.08, 600) + [
        ("destello", "rotation", 0, 25, 700, 150),
        *escala("destello", 1.2, 700, 150),
    ],
    "ill_email_sent": [
        ("sobre", "translateX", 0, 5, 600, 0),
        ("estela", "translateX", 0, -6, 600, 0),
        *escala("check", 1.12, 350, 450),
    ],
    "ill_onboarding_alert": [
        ("mercurio", "scaleY", 1, 0.55, 900, 0),
        *escala("aviso", 1.12, 400, 700),
    ],
    "ill_onboarding_link": [
        ("candado", "translateY", 0, -4, 700, 0),
        *escala("listo", 1.2, 400, 400),
    ],
    "ill_reports_empty": [
        ("lupa", "translateX", 0, -48, 1100, 0),
        ("lupa", "rotation", 0, -12, 1100, 0),
    ],
}


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


def path_xml(e, sangria):
    etiqueta = e.tag.replace(SVG, "")
    if etiqueta == "rect":
        datos = rect_a_path(e)
    elif etiqueta == "circle":
        datos = circle_a_path(e)
    else:
        datos = " ".join(e.get("d").split())
    atributos = []
    if e.get("id"):
        atributos.append(f'android:name="{e.get("id")}"')
    atributos.append(f'android:pathData="{datos}"')
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
    separador = "\n" + sangria + "    "
    return sangria + "<path" + separador + separador.join(atributos) + " />"


def hijos_xml(padre, sangria):
    lineas = []
    for e in padre:
        etiqueta = e.tag.replace(SVG, "")
        if etiqueta in ("rect", "circle", "path"):
            lineas.append(path_xml(e, sangria))
        elif etiqueta == "g":
            atributos = []
            if e.get("id"):
                atributos.append(f'android:name="{e.get("id")}"')
            if e.get("data-pivot"):
                px, py = e.get("data-pivot").split(",")
                atributos += [f'android:pivotX="{num(px)}"', f'android:pivotY="{num(py)}"']
            lineas.append(sangria + "<group " + " ".join(atributos) + ">")
            lineas += hijos_xml(e, sangria + "    ")
            lineas.append(sangria + "</group>")
    return lineas


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
    lineas += hijos_xml(raiz, "    ")
    lineas.append("</vector>")
    destino.write_text("\n".join(lineas) + "\n", encoding="utf-8")


def animador(propiedad, desde, hasta, duracion, retraso):
    # Va de "desde" a "hasta" y vuelve, con una curva suave.
    base = (
        f'android:propertyName="{propiedad}" android:valueType="floatType" '
        f'android:interpolator="{SUAVE}" android:duration="{duracion}"'
    )
    return (
        '                <set android:ordering="sequentially">\n'
        f'                    <objectAnimator {base} android:startOffset="{retraso}"\n'
        f'                        android:valueFrom="{num(desde)}" android:valueTo="{num(hasta)}" />\n'
        f'                    <objectAnimator {base}\n'
        f'                        android:valueFrom="{num(hasta)}" android:valueTo="{num(desde)}" />\n'
        "                </set>"
    )


def generar_avd(nombre, sufijo):
    partes = {}
    for parte, *datos in ANIMACIONES[nombre]:
        partes.setdefault(parte, []).append(animador(*datos))
    lineas = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- Generado con docs/ilustraciones/convertir.py (tabla ANIMACIONES). -->",
        '<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt"',
        f'    android:drawable="@drawable/{nombre}{sufijo}">',
    ]
    for parte, animadores in partes.items():
        lineas += [
            f'    <target android:name="{parte}">',
            '        <aapt:attr name="android:animation">',
            "            <set>",
            *animadores,
            "            </set>",
            "        </aapt:attr>",
            "    </target>",
        ]
    lineas.append("</animated-vector>")
    (DESTINO / f"avd_{nombre}{sufijo}.xml").write_text("\n".join(lineas) + "\n", encoding="utf-8")


for carpeta, sufijo in SUFIJOS.items():
    for svg in sorted((pathlib.Path(__file__).parent / carpeta).glob("*.svg")):
        destino = DESTINO / f"{svg.stem}{sufijo}.xml"
        convertir(svg, destino)
        mensaje = f"{carpeta}/{svg.name} -> {destino.relative_to(RAIZ)}"
        if svg.stem in ANIMACIONES:
            generar_avd(svg.stem, sufijo)
            mensaje += f" (+ avd_{svg.stem}{sufijo}.xml)"
        print(mensaje)
