package tp20260054.eroutes.usuarios.dto;

import tp20260054.eroutes.usuarios.TipoVehiculo;

public record VehiculoDto(
        Long id,
        String alias,
        String marca,
        String modelo,
        TipoVehiculo tipo,
        int velocidadMaximaKmh,
        String placa,
        String categoriaReglamentaria,
        boolean puedeUsarCiclovia,
        boolean activo) {
}
