package tp20260054.eroutes.recalculo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Beans del módulo de recálculo que requieren construcción paramétrica. */
@Configuration
public class RecalculoConfig {

    @Bean
    DetectorDesvio detectorDesvio(RecalculoProperties props) {
        return new DetectorDesvio(props.umbralDesvioM(), props.muestrasDesvio());
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
