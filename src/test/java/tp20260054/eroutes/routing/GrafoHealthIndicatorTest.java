package tp20260054.eroutes.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import tp20260054.eroutes.routing.graph.GrafoHealthIndicator;
import tp20260054.eroutes.routing.graph.GrafoProvider;

/** Indicador de salud del grafo. Sin Spring ni base de datos. */
class GrafoHealthIndicatorTest {

    @Test
    void grafoPrecargado_estaUp() {
        GrafoProvider provider = mock(GrafoProvider.class);
        when(provider.estaPrecargado()).thenReturn(true);

        assertThat(new GrafoHealthIndicator(provider).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void grafoSinCargar_estaDown() {
        GrafoProvider provider = mock(GrafoProvider.class);
        when(provider.estaPrecargado()).thenReturn(false);

        assertThat(new GrafoHealthIndicator(provider).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
