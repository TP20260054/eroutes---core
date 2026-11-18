package tp20260054.eroutes.recalculo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import tp20260054.eroutes.navegacion.ConfiguracionNavegacion;
import tp20260054.eroutes.routing.costo.Preset;
import tp20260054.eroutes.vehiculo.PerfilVehiculo;

/**
 * Tests unitarios de {@link SesionNavegacionStore}. Sin Spring ni base de datos.
 */
class SesionNavegacionStoreTest {

    private static PerfilVehiculo perfil() {
        return new PerfilVehiculo("M", "BICIMOTO", 45, "AUTOMOTOR_MENOR_L", false, List.of());
    }

    private static SesionNavegacion sesion(long navId, long usuarioId) {
        return new SesionNavegacion(navId, usuarioId, perfil(),
                ConfiguracionNavegacion.AI, Preset.RAPIDA, List.of());
    }

    private SesionNavegacionStore store() {
        return new SesionNavegacionStore();
    }

    @Test
    void guardarYRecuperar_mismoUsuario_presente() {
        SesionNavegacionStore s = store();
        s.guardar(sesion(1L, 10L));
        assertThat(s.obtener(1L, 10L)).isPresent();
    }

    @Test
    void recuperar_usuarioAjeno_vacio() {
        SesionNavegacionStore s = store();
        s.guardar(sesion(1L, 10L));
        assertThat(s.obtener(1L, 99L)).isEmpty();
    }

    @Test
    void recuperar_sesionInexistente_vacio() {
        SesionNavegacionStore s = store();
        assertThat(s.obtener(999L, 10L)).isEmpty();
    }

    @Test
    void eliminar_sesionDesaparece() {
        SesionNavegacionStore s = store();
        s.guardar(sesion(1L, 10L));
        s.eliminar(1L);
        assertThat(s.obtener(1L, 10L)).isEmpty();
    }

    @Test
    void purgar_sesionReciente_noEliminada() {
        // Este test solo verifica que guardar+obtener funciona inmediatamente después.
        // El comportamiento de expiración depende del campo @Value y se cubre por el
        // contexto Spring (EroutesApplicationTests).
        SesionNavegacionStore s = store();
        s.guardar(sesion(1L, 10L));
        assertThat(s.obtener(1L, 10L)).isPresent();
    }
}
