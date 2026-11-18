-- Persistencia del módulo DQL de recálculo de rutas.
--
--   eventos_viales : precursor de Report / TEMPORAL_BLOCK (OB2 5.1)
--   navegaciones   : equivale a Navigation (OB2 5.1)
--   recalculos     : equivale a Recalculation (OB2 5.1) + campos de evaluación (OB2 8.3/8.4)
--
-- Retención OB2 9.1: los puntos origen y destino de cada navegación se conservan
-- hasta 30 días después del cierre del OE4. La purga NO se implementa en esta
-- migración; queda pendiente para una tarea de mantenimiento posterior.

CREATE TABLE eventos_viales (
    id                BIGSERIAL      PRIMARY KEY,
    tipo              VARCHAR(30)    NOT NULL CHECK (tipo IN ('BLOQUEO_TOTAL','OBRA','BACHES_SEVEROS','VEHICULO_EN_CICLOVIA')),
    gravedad          DOUBLE PRECISION NOT NULL CHECK (gravedad BETWEEN 0 AND 1),
    tramo_id          BIGINT         NOT NULL REFERENCES tramos(id),
    geom              geometry(Point,4326) NOT NULL,
    estado            VARCHAR(10)    NOT NULL CHECK (estado IN ('ACTIVO','EXPIRADO','RESUELTO')),
    autor_usuario_id  BIGINT         NOT NULL REFERENCES usuarios(id),
    motivo            VARCHAR(255),
    creado_en         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    expira_en         TIMESTAMPTZ    NOT NULL,
    resuelto_en       TIMESTAMPTZ
);
CREATE INDEX ix_eventos_geom   ON eventos_viales USING GIST (geom);
CREATE INDEX ix_eventos_estado ON eventos_viales (estado, expira_en);
CREATE INDEX ix_eventos_tramo  ON eventos_viales (tramo_id);

CREATE TABLE navegaciones (
    id                BIGSERIAL      PRIMARY KEY,
    usuario_id        BIGINT         NOT NULL REFERENCES usuarios(id),
    vehiculo_id       BIGINT         NOT NULL REFERENCES vehiculos(id),
    origen            geometry(Point,4326) NOT NULL,
    destino           geometry(Point,4326) NOT NULL,
    etiqueta          VARCHAR(10)    NOT NULL CHECK (etiqueta IN ('RAPIDA','CORTA','SEGURA')),
    distancia_m       DOUBLE PRECISION NOT NULL,
    tiempo_estimado_s DOUBLE PRECISION NOT NULL,
    pesos_usados      JSONB          NOT NULL,
    configuracion     VARCHAR(10)    NOT NULL CHECK (configuracion IN ('Reference','RULE','AI')),
    creado_en         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE recalculos (
    id                       BIGSERIAL      PRIMARY KEY,
    navegacion_id            BIGINT         NOT NULL REFERENCES navegaciones(id) ON DELETE CASCADE,
    disparador               VARCHAR(10)    NOT NULL CHECK (disparador IN ('BLOCKED','REROUTE','POI','MANUAL')),
    accion                   SMALLINT       NOT NULL CHECK (accion IN (0,1,2)),
    configuracion            VARCHAR(10)    NOT NULL,
    estado_entrada           JSONB          NOT NULL,          -- vector + crudos; SIN coordenadas
    tiempo_inferencia_ms     DOUBLE PRECISION,
    tiempo_calculo_ms        DOUBLE PRECISION,
    tiempo_total_servidor_ms DOUBLE PRECISION,
    policy_version           VARCHAR(60)    NOT NULL,
    uso_respaldo             BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_respaldo          VARCHAR(30),
    pesos_usados             JSONB,
    ruta_anterior_bloqueada  BOOLEAN,
    costo_anterior           DOUBLE PRECISION,    -- NULL si estaba bloqueada
    costo_nuevo              DOUBLE PRECISION,
    innecesario              BOOLEAN,             -- etiqueta para OE4 (ver 3.6)
    exito                    BOOLEAN       NOT NULL,
    mensaje                  VARCHAR(255),
    creado_en                TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_recalculos_nav ON recalculos (navegacion_id, creado_en);

-- Retención OB2 9.1 (la purga no se implementa en esta migración):
COMMENT ON TABLE  navegaciones IS 'Sesiones de navegación (OB2 5.1). Los puntos origen y destino se conservan hasta 30 días después del cierre del OE4 (OB2 9.1). La purga no se implementa en esta migración.';
COMMENT ON COLUMN navegaciones.origen  IS 'Punto de origen de la navegación. Retención OB2 9.1: conservar hasta 30 días tras el cierre del OE4.';
COMMENT ON COLUMN navegaciones.destino IS 'Punto de destino de la navegación. Retención OB2 9.1: conservar hasta 30 días tras el cierre del OE4.';
