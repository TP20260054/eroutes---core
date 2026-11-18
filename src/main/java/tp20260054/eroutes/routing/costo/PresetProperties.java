package tp20260054.eroutes.routing.costo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pesos normalizados de cada preset. Cada preset debe sumar 1
 * (ver {@link PesosPreset}).
 */
@ConfigurationProperties(prefix = "eroutes.routing.presets")
public record PresetProperties(
        PesosPreset rapida,
        PesosPreset corta,
        PesosPreset segura) {

    public PesosPreset de(Preset preset) {
        return switch (preset) {
            case RAPIDA -> rapida;
            case CORTA -> corta;
            case SEGURA -> segura;
        };
    }
}
