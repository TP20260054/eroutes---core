CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE tramos
    ADD COLUMN nombre      VARCHAR(200),
    ADD COLUMN nombre_norm VARCHAR(200);

CREATE TABLE lugares (
    id          BIGSERIAL PRIMARY KEY,
    osm_tipo    VARCHAR(10)  NOT NULL,
    osm_id      BIGINT       NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    nombre_norm VARCHAR(200) NOT NULL,
    categoria   VARCHAR(80),
    direccion   VARCHAR(200),
    geom        geometry(Point, 4326) NOT NULL,
    UNIQUE (osm_tipo, osm_id)
);

CREATE INDEX idx_lugares_geom        ON lugares USING GIST (geom);
CREATE INDEX idx_lugares_nombre_trgm ON lugares USING GIN (nombre_norm gin_trgm_ops);

CREATE MATERIALIZED VIEW calles AS
SELECT nombre, nombre_norm,
       ST_Multi(ST_Collect(geom))::geometry(MultiLineString, 4326) AS geom
FROM tramos
WHERE nombre IS NOT NULL
GROUP BY nombre, nombre_norm;

CREATE INDEX idx_calles_geom        ON calles USING GIST (geom);
CREATE INDEX idx_calles_nombre_trgm ON calles USING GIN (nombre_norm gin_trgm_ops);
