CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE nodos (
    id   BIGINT PRIMARY KEY,
    geom geometry(Point, 4326) NOT NULL
);

CREATE TABLE tramos (
    id            BIGSERIAL PRIMARY KEY,
    osm_way_id    BIGINT,
    nodo_origen   BIGINT NOT NULL REFERENCES nodos(id),
    nodo_destino  BIGINT NOT NULL REFERENCES nodos(id),
    geom          geometry(LineString, 4326) NOT NULL,
    longitud_m    DOUBLE PRECISION NOT NULL,
    tipo_via      VARCHAR(50),
    velocidad_max INTEGER,
    es_ciclovia   BOOLEAN NOT NULL DEFAULT FALSE,
    validado      BOOLEAN NOT NULL DEFAULT FALSE,
    sentido_unico BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_tramos_geom    ON tramos USING GIST (geom);
CREATE INDEX idx_tramos_origen  ON tramos (nodo_origen);
CREATE INDEX idx_tramos_destino ON tramos (nodo_destino);
CREATE INDEX idx_nodos_geom     ON nodos  USING GIST (geom);
