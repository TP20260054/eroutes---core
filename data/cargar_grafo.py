"""Carga el grafo vial de OSM (vía OSMnx) en las tablas nodos/tramos de Neon.

Uso (desde la raíz del backend, donde está .env.local):
    python data/cargar_grafo.py --place "San Isidro, Lima, Peru" --reset
"""
import argparse
import os
import re

import osmnx as ox
import psycopg2
from dotenv import load_dotenv
from psycopg2.extras import execute_values

CICLO_TAGS = ["cycleway", "cycleway:left", "cycleway:right", "cycleway:both"]
DEDICADA = {"lane", "track", "separate", "opposite_lane", "opposite_track"}

ox.settings.useful_tags_way = list(set(ox.settings.useful_tags_way + CICLO_TAGS))
ox.settings.log_console = True


def first(v):
    """OSMnx a veces devuelve listas o NaN; normaliza a un valor simple o None."""
    if isinstance(v, (list, tuple)):
        return v[0] if v else None
    return None if v != v else v  # NaN -> None


def parse_speed(v):
    v = first(v)
    if v is None:
        return None
    m = re.match(r"\d+", str(v))
    return int(m.group()) if m else None


def is_true(v):
    return str(first(v)).lower() in ("true", "yes", "1")


def es_ciclovia(row):
    if first(row.get("highway")) == "cycleway":
        return True
    for tag in CICLO_TAGS:
        vals = row.get(tag)
        vals = vals if isinstance(vals, (list, tuple)) else [vals]
        if any(v in DEDICADA for v in vals):
            return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--place", required=True)
    ap.add_argument("--env", default=".env.local")
    ap.add_argument("--reset", action="store_true",
                    help="vacía nodos y tramos antes de cargar")
    args = ap.parse_args()

    load_dotenv(args.env)
    dsn = os.environ["DATABASE_URL_UNPOOLED"]  # conexión directa, sin pooler

    print(f"Descargando grafo de: {args.place}")
    G = ox.graph_from_place(args.place, network_type="all", simplify=True)
    nodes, edges = ox.graph_to_gdfs(G)
    edges = edges.reset_index()  # columnas u, v, key
    print(f"{len(nodes)} nodos, {len(edges)} tramos")

    nodo_rows = [(int(osmid), g.wkt) for osmid, g in zip(nodes.index, nodes.geometry)]
    tramo_rows = [
        (
            int(first(r.get("osmid"))), int(r["u"]), int(r["v"]),
            r["geometry"].wkt, float(r["length"]),
            first(r.get("highway")), parse_speed(r.get("maxspeed")),
            es_ciclovia(r), is_true(r.get("oneway")),
        )
        for r in edges.to_dict("records")
    ]

    with psycopg2.connect(dsn) as conn, conn.cursor() as cur:
        if args.reset:
            cur.execute("TRUNCATE tramos, nodos RESTART IDENTITY")
        execute_values(
            cur,
            "INSERT INTO nodos (id, geom) VALUES %s ON CONFLICT (id) DO NOTHING",
            nodo_rows, template="(%s, ST_GeomFromText(%s, 4326))", page_size=1000)
        execute_values(
            cur,
            """INSERT INTO tramos (osm_way_id, nodo_origen, nodo_destino, geom,
                                   longitud_m, tipo_via, velocidad_max,
                                   es_ciclovia, sentido_unico) VALUES %s""",
            tramo_rows,
            template="(%s, %s, %s, ST_GeomFromText(%s, 4326), %s, %s, %s, %s, %s)",
            page_size=1000)
    print("Carga completa.")


if __name__ == "__main__":
    main()
