package tp20260054.eroutes.navegacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Retención de coordenadas de navegaciones (OB2 9.1). Sin Spring ni base de datos. */
class PurgaNavegacionesTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 6, 30, 3, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void purga_conRetencionDe30Dias_usaComoLimiteHace30Dias() {
        NavegacionRepository repo = mock(NavegacionRepository.class);
        when(repo.purgarCoordenadasAnterioresA(any())).thenReturn(4);

        int n = new PurgaNavegaciones(repo, 30).purgar(AHORA);

        ArgumentCaptor<OffsetDateTime> limite = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repo).purgarCoordenadasAnterioresA(limite.capture());
        assertThat(limite.getValue()).isEqualTo(AHORA.minusDays(30));
        assertThat(n).isEqualTo(4);
    }

    @Test
    void purga_respetaLaRetencionConfigurada() {
        NavegacionRepository repo = mock(NavegacionRepository.class);

        new PurgaNavegaciones(repo, 7).purgar(AHORA);

        verify(repo).purgarCoordenadasAnterioresA(AHORA.minusDays(7));
    }

    @Test
    void purga_conRetencionInvalida_noPurgaNada() {
        NavegacionRepository repo = mock(NavegacionRepository.class);

        assertThat(new PurgaNavegaciones(repo, 0).purgar(AHORA)).isZero();
        assertThat(new PurgaNavegaciones(repo, -5).purgar(AHORA)).isZero();

        verify(repo, never()).purgarCoordenadasAnterioresA(any());
    }

    @Test
    void tareaDiaria_ejecutaLaPurgaConLaHoraActual() {
        NavegacionRepository repo = mock(NavegacionRepository.class);
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);

        new PurgaNavegaciones(repo, 30).purgarDiaria();

        ArgumentCaptor<OffsetDateTime> limite = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repo).purgarCoordenadasAnterioresA(limite.capture());
        assertThat(limite.getValue()).isAfterOrEqualTo(antes)
                .isBefore(antes.plusMinutes(1));
    }

    @Test
    void entidad_permiteOrigenYDestinoNulos() {
        Navegacion nav = new Navegacion();
        nav.setOrigen(null);
        nav.setDestino(null);

        assertThat(nav.getOrigen()).isNull();
        assertThat(nav.getDestino()).isNull();
    }
}
