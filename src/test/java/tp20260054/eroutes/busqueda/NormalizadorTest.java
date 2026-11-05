package tp20260054.eroutes.busqueda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizadorTest {

    // ---- Casos canónicos del enunciado ----

    @Test
    void joseGalvezBarrenechea() {
        assertThat(Normalizador.normalizar("José Gálvez Barrenechea"))
                .isEqualTo("jose galvez barrenechea");
    }

    @Test
    void avRepublicaDePanama() {
        assertThat(Normalizador.normalizar("Av. República de Panamá"))
                .isEqualTo("av republica de panama");
    }

    @Test
    void parqueElOlivar() {
        assertThat(Normalizador.normalizar("Parque El Olivar"))
                .isEqualTo("parque el olivar");
    }

    // ---- Tildes y ñ ----

    @Test
    void tildesYEnye() {
        assertThat(Normalizador.normalizar("cañón árabe éxito übermenschen"))
                .isEqualTo("canon arabe exito ubermenschen");
    }

    // ---- Símbolos y puntuación ----

    @Test
    void simbolosSustituidos() {
        assertThat(Normalizador.normalizar("Jr. de la Unión #123"))
                .isEqualTo("jr de la union 123");
    }

    // ---- Espacios múltiples ----

    @Test
    void espaciosMultiples() {
        assertThat(Normalizador.normalizar("  av   los   pinos  "))
                .isEqualTo("av los pinos");
    }

    // ---- null y vacío ----

    @Test
    void nulo() {
        assertThat(Normalizador.normalizar(null)).isEmpty();
    }

    @Test
    void vacio() {
        assertThat(Normalizador.normalizar("")).isEmpty();
    }

    // ---- sinTipo ----

    @Test
    void sinTipoAvJavierPrado() {
        assertThat(Normalizador.sinTipo("av javier prado"))
                .isEqualTo("javier prado");
    }

    @Test
    void sinTipoJrDeLaUnion() {
        // "jr" es tipo vía: quitar primer token
        String norm = Normalizador.normalizar("Jr. de la Union");   // "jr de la union"
        assertThat(Normalizador.sinTipo(norm)).isEqualTo("de la union");
    }

    @Test
    void sinTipoTokenUnicoAvenidaNoSeRecorta() {
        // Un solo token que es tipo vía NO se recorta (necesita >1 token)
        assertThat(Normalizador.sinTipo("avenida")).isEqualTo("avenida");
    }

    @Test
    void sinTipoSinPrefijo() {
        assertThat(Normalizador.sinTipo("javier prado")).isEqualTo("javier prado");
    }

    @Test
    void sinTipoNuloDevuelveNulo() {
        assertThat(Normalizador.sinTipo(null)).isNull();
    }
}
