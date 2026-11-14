package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tp20260054.eroutes.usuarios.TipoVehiculo;
import tp20260054.eroutes.usuarios.Vehiculo;
import tp20260054.eroutes.usuarios.VehiculoMapper;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Verifica la regla reglamentaria derivada del perfil de vehículo.
 * Sin Spring: lógica pura desde VehiculoMapper.
 */
class PerfilVehiculoTest {

    private static Vehiculo vehiculo(int vel) {
        Vehiculo v = new Vehiculo();
        v.setId(1L);
        v.setModelo("Kumai");
        v.setMarca("Aima");
        v.setTipo(TipoVehiculo.BICIMOTO);
        v.setVelocidadMaximaKmh(vel);
        v.setCreadoEn(OffsetDateTime.now());
        v.setActualizadoEn(OffsetDateTime.now());
        return v;
    }

    @Test
    void vel45_esAutomotorMenor_noPuedeCiclovia() {
        PerfilVehiculo p = VehiculoMapper.toPerfilVehiculo(vehiculo(45));
        assertThat(p.categoriaReglamentaria()).isEqualTo("AUTOMOTOR_MENOR_L");
        assertThat(p.puedeUsarCiclovia()).isFalse();
        assertThat(p.tiposViaExcluidos()).contains("cycleway");
    }

    @Test
    void vel25_esMovilidadPersonal_puedeCiclovia() {
        PerfilVehiculo p = VehiculoMapper.toPerfilVehiculo(vehiculo(25));
        assertThat(p.categoriaReglamentaria()).isEqualTo("MOVILIDAD_PERSONAL");
        assertThat(p.puedeUsarCiclovia()).isTrue();
        assertThat(p.tiposViaExcluidos()).doesNotContain("cycleway");
    }

    @Test
    void vel20_esMovilidadPersonal_puedeCiclovia() {
        PerfilVehiculo p = VehiculoMapper.toPerfilVehiculo(vehiculo(20));
        assertThat(p.categoriaReglamentaria()).isEqualTo("MOVILIDAD_PERSONAL");
        assertThat(p.puedeUsarCiclovia()).isTrue();
        assertThat(p.tiposViaExcluidos()).doesNotContain("cycleway");
    }

    @Test
    void ambosPerfiles_excluyenFootwayYMotorway() {
        PerfilVehiculo mot = VehiculoMapper.toPerfilVehiculo(vehiculo(45));
        PerfilVehiculo per = VehiculoMapper.toPerfilVehiculo(vehiculo(25));
        for (String tv : new String[]{"footway","pedestrian","path","steps","motorway","motorway_link"}) {
            assertThat(mot.tiposViaExcluidos()).contains(tv);
            assertThat(per.tiposViaExcluidos()).contains(tv);
        }
    }
}
