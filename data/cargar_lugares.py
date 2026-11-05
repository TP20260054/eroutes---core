"""Carga lugares de interés de OSM en la tabla lugares de Neon.

Uso:
    python data/cargar_lugares.py --place "San Isidro, Lima, Peru" --reset
"""
import argparse
import os
import sys
from pathlib import Path

import osmnx as ox
import psycopg2
from dotenv import load_dotenv
from psycopg2.extras import execute_values

# Permite importar normalizar.py desde la misma carpeta data/
sys.path.insert(0, str(Path(__file__).parent))
from normalizar import normalizar  # noqa: E402

TAGS_BUSQUEDA = {
    "amenity": True,
    "shop": True,
    "tourism": True,
    "leisure": True,
    "office": True,
}

# Orden de prioridad para determinar la categoría del lugar
CLAVES_CATEGORIA = ["amenity", "shop", "tourism", "leisure", "office"]


def categoria_de(row) -> str | None:
    """Devuelve 'clave:valor' con la primera clave presente en CLAVES_CATEGORIA."""
    for clave in CLAVES_CATEGORIA:
        valor = row.get(clave)
        if valor and str(valor).lower() not in ("nan", "none", ""):
            return f"{clave}:{valor}"
    return None


def none_if_nan(v) -> str | None:
    """Convierte NaN y 'nan' a None."""
    if v is None:
        return None
    s = str(v)
    if s.lower() in ("nan", "none", ""):
        return None
    return s


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--place", required=True)
    ap.add_argument("--env", default=".env.local")
    ap.add_argument("--reset", action="store_true",
                    help="trunca la tabla lugares antes de cargar")
    args = ap.parse_args()

    load_dotenv(args.env)
    dsn = os.environ["DATABASE_URL_UNPOOLED"]

    print(f"Descargando lugares de: {args.place}")
    gdf = ox.features_from_place(args.place, tags=TAGS_BUSQUEDA)
    print(f"Filas descargadas: {len(gdf)}")

    # Detectar los nombres reales de los niveles del índice
    index_names = list(gdf.index.names)
    print(f"Niveles del índice: {index_names}")

    # OSMnx ≥ 1.x usa ('element_type', 'osmid');
    # versiones anteriores pueden usar ('osmid',) o ('element', 'id')
    if len(index_names) == 2:
        nivel_tipo, nivel_id = index_names[0], index_names[1]
    elif len(index_names) == 1:
        nivel_tipo = None
        nivel_id = index_names[0]
    else:
        raise RuntimeError(f"Estructura de índice desconocida: {index_names}")

    gdf = gdf.reset_index()

    # Conservar solo filas con name
    gdf = gdf[gdf["name"].notna() & (gdf["name"] != "")]
    print(f"Filas con nombre: {len(gdf)}")

    rows = []
    for _, row in gdf.iterrows():
        nombre = none_if_nan(row.get("name"))
        if not nombre:
            continue

        # osm_tipo y osm_id desde el índice
        if nivel_tipo:
            osm_tipo = str(row.get(nivel_tipo, "node"))[:10]
        else:
            osm_tipo = "node"
        osm_id = int(row.get(nivel_id, 0))

        # Geometría: punto representativo en 4326
        geom = row.get("geometry")
        if geom is None:
            continue
        punto = geom.representative_point()
        punto_wkt = punto.wkt

        nombre_norm = normalizar(nombre)
        cat = categoria_de(row)

        # Dirección: addr:street + addr:housenumber
        calle = none_if_nan(row.get("addr:street"))
        numero = none_if_nan(row.get("addr:housenumber"))
        if calle and numero:
            direccion = f"{calle} {numero}"
        elif calle:
            direccion = calle
        else:
            direccion = None

        rows.append((osm_tipo, osm_id, nombre, nombre_norm, cat, direccion, punto_wkt))

    print(f"Filas a insertar/actualizar: {len(rows)}")

    with psycopg2.connect(dsn) as conn, conn.cursor() as cur:
        if args.reset:
            cur.execute("TRUNCATE lugares RESTART IDENTITY")
        execute_values(
            cur,
            """INSERT INTO lugares (osm_tipo, osm_id, nombre, nombre_norm, categoria, direccion, geom)
               VALUES %s
               ON CONFLICT (osm_tipo, osm_id) DO UPDATE SET
                   nombre     = EXCLUDED.nombre,
                   nombre_norm = EXCLUDED.nombre_norm,
                   categoria  = EXCLUDED.categoria,
                   direccion  = EXCLUDED.direccion,
                   geom       = EXCLUDED.geom""",
            rows,
            template="(%s, %s, %s, %s, %s, %s, ST_GeomFromText(%s, 4326))",
            page_size=500)
    print("Carga de lugares completa.")


if __name__ == "__main__":
    main()
