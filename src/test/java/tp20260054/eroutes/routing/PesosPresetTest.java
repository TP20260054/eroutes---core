package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tp20260054.eroutes.routing.costo.PesosPreset;

class PesosPresetTest {

    @Test
    void sumaUno_seConstruye() {
        PesosPreset p = new PesosPreset(0.2, 0.2, 0.2, 0.2, 0.2);
        assertThat(p.tiempo()).isEqualTo(0.2);
        assertThat(p.cruce()).isEqualTo(0.2);
    }

    @Test
    void sumaMayorQueUno_lanza() {
        assertThatThrownBy(() -> new PesosPreset(0.5, 0.5, 0.5, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sumar 1");
    }

    @Test
    void sumaMenorQueUno_lanza() {
        assertThatThrownBy(() -> new PesosPreset(0.1, 0.1, 0.1, 0.1, 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sumar 1");
    }
}
