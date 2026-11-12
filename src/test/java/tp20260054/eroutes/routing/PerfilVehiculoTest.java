package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifica la regla reglamentaria derivada del perfil de vehículo.
 * Sin Spring: lógica pura.
 */
class PerfilVehiculoTest {

    private static tp20260054.eroutes.vehiculo.PerfilVehiculo perfil(int vel, String catMotorizado, String catPersonal) {
        boolean esMotorizado = vel > 25;
        String categoria = esMotorizado ? "AUTOMOTOR_MENOR_L" : "MOVILIDAD_PERSONAL";
        boolean puedeUsarCiclovia = !esMotorizado;
        java.util.List<String> excluidos = esMotorizado
                ? java.util.List.of("footway","pedestrian","path","steps","corridor",
                    "bridleway","cycleway","motorway","motorway_link","trunk","trunk_link")
                : java.util.List.of("footway","pedestrian","path","steps","corridor",
                    "bridleway","motorway","motorway_link","trunk","trunk_link");
        return new tp20260054.eroutes.vehiculo.PerfilVehiculo(
                "modelo", "tipo", vel, categoria, puedeUsarCiclovia, excluidos);
    }

    @Test
    void vel45_esAutomotorMenor_noPuedeCiclovia() {
        var p = perfil(45, null, null);
        assertThat(p.categoriaReglamentaria()).isEqualTo("AUTOMOTOR_MENOR_L");
        assertThat(p.puedeUsarCiclovia()).isFalse();
        assertThat(p.tiposViaExcluidos()).contains("cycleway");
    }

    @Test
    void vel25_esMovilidadPersonal_puedeCiclovia() {
        var p = perfil(25, null, null);
        assertThat(p.categoriaReglamentaria()).isEqualTo("MOVILIDAD_PERSONAL");
        assertThat(p.puedeUsarCiclovia()).isTrue();
        assertThat(p.tiposViaExcluidos()).doesNotContain("cycleway");
    }

    @Test
    void vel20_esMovilidadPersonal_puedeCiclovia() {
        var p = perfil(20, null, null);
        assertThat(p.categoriaReglamentaria()).isEqualTo("MOVILIDAD_PERSONAL");
        assertThat(p.puedeUsarCiclovia()).isTrue();
        assertThat(p.tiposViaExcluidos()).doesNotContain("cycleway");
    }

    @Test
    void ambosPerfiles_excluyenFootwayYMotorway() {
        var mot = perfil(45, null, null);
        var per = perfil(25, null, null);
        for (String tv : new String[]{"footway","pedestrian","path","steps","motorway","motorway_link"}) {
            assertThat(mot.tiposViaExcluidos()).contains(tv);
            assertThat(per.tiposViaExcluidos()).contains(tv);
        }
    }
}
