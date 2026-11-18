-- Estandariza el valor 'Reference' (mixed-case) a 'REFERENCE' en la columna
-- configuracion de navegaciones para alinear con el enum ConfiguracionNavegacion.
-- La tabla estaba vacía al aplicar esta migración.

ALTER TABLE navegaciones
    DROP CONSTRAINT IF EXISTS navegaciones_configuracion_check;

UPDATE navegaciones SET configuracion = 'REFERENCE' WHERE configuracion = 'Reference';

ALTER TABLE navegaciones
    ADD CONSTRAINT navegaciones_configuracion_check
        CHECK (configuracion IN ('REFERENCE','RULE','AI'));
