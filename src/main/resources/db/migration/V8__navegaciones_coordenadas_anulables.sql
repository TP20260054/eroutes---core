-- Retención OB2 9.1: la tarea de purga pone en NULL el origen y el destino de las
-- navegaciones con más de eroutes.navegacion.retencion-dias días. Para poder hacerlo
-- las columnas dejan de ser NOT NULL. Migración aditiva: no cambia datos existentes.
ALTER TABLE navegaciones
    ALTER COLUMN origen  DROP NOT NULL,
    ALTER COLUMN destino DROP NOT NULL;
