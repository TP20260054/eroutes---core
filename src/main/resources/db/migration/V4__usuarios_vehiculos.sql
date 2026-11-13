CREATE TABLE usuarios (
    id                           BIGSERIAL PRIMARY KEY,
    correo                       VARCHAR(160) NOT NULL,
    contrasena_hash              VARCHAR(100) NOT NULL,
    nombres                      VARCHAR(100) NOT NULL,
    apellidos                    VARCHAR(100) NOT NULL,
    telefono                     VARCHAR(20),
    contacto_emergencia_nombre   VARCHAR(100),
    contacto_emergencia_telefono VARCHAR(20),
    vehiculo_activo_id           BIGINT,
    creado_en                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_usuarios_correo ON usuarios (lower(correo));

CREATE TABLE vehiculos (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_id           BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    alias                VARCHAR(60),
    marca                VARCHAR(60),
    modelo               VARCHAR(80) NOT NULL,
    tipo                 VARCHAR(30) NOT NULL,
    velocidad_maxima_kmh INTEGER NOT NULL CHECK (velocidad_maxima_kmh BETWEEN 5 AND 80),
    placa                VARCHAR(10),
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehiculos_usuario ON vehiculos (usuario_id);

ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_vehiculo_activo
    FOREIGN KEY (vehiculo_activo_id) REFERENCES vehiculos(id) ON DELETE SET NULL;
