"""Normalización de texto para búsqueda de calles y lugares.

Algoritmo idéntico al de Normalizador.java:
    NFD → quitar diacríticos → minúsculas → reemplazar [^a-z0-9 ] por espacio
    → colapsar espacios → strip.
"""
import unicodedata
import re

_TIPOS_VIA = {
    "av", "avenida", "jr", "jiron", "ca", "calle",
    "psj", "pasaje", "alameda", "malecon",
}


def normalizar(texto: str | None) -> str:
    """Normaliza un nombre para búsqueda. Devuelve '' si la entrada es None o vacía."""
    if not texto:
        return ""
    # 1. NFD + quitar marcas diacríticas (categoría Mn)
    nfd = unicodedata.normalize("NFD", texto)
    sin_tildes = "".join(c for c in nfd if unicodedata.category(c) != "Mn")
    # 2. Minúsculas
    lower = sin_tildes.lower()
    # 3. Reemplazar todo carácter que no sea [a-z0-9 ] por espacio
    limpio = re.sub(r"[^a-z0-9 ]", " ", lower)
    # 4. Colapsar espacios y strip
    return re.sub(r" +", " ", limpio).strip()


def sin_tipo(consulta_norm: str) -> str:
    """Genera la forma sin tipo de vía para usar como filtro LIKE.

    Si la consulta normalizada tiene más de un token y el primero
    es un prefijo de tipo de vía, devuelve la consulta sin ese primer token.
    """
    if not consulta_norm:
        return consulta_norm
    tokens = consulta_norm.split()
    if len(tokens) > 1 and tokens[0] in _TIPOS_VIA:
        return consulta_norm[len(tokens[0]):].strip()
    return consulta_norm
