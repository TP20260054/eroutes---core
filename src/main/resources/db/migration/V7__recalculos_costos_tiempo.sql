-- Comparación en segundos de la ruta anterior y la nueva en cada recálculo.
-- Ambas columnas se calculan con la MISMA fórmula de tiempo (CostoTiempo, con demoras por nodo)
-- sobre el mismo tramo restante desde la posición actual. Migración aditiva y nullable.
ALTER TABLE recalculos
    ADD COLUMN costo_anterior_tiempo_s DOUBLE PRECISION,
    ADD COLUMN costo_nuevo_tiempo_s    DOUBLE PRECISION;
