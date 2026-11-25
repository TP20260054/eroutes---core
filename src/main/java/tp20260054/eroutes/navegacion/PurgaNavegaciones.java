package tp20260054.eroutes.navegacion;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retención OB2 9.1: una vez al día pone en NULL las coordenadas de origen y destino de
 * las navegaciones con más de {@code eroutes.navegacion.retencion-dias} días. El resto de
 * la fila (tiempos, costos, configuración) se conserva para la evaluación.
 *
 * <p>Una retención menor a 1 día se considera mal configurada y no purga nada.
 */
@Component
public class PurgaNavegaciones {

    private static final Logger log = LoggerFactory.getLogger(PurgaNavegaciones.class);

    private final NavegacionRepository repo;
    private final int retencionDias;

    public PurgaNavegaciones(NavegacionRepository repo,
                             @Value("${eroutes.navegacion.retencion-dias:30}") int retencionDias) {
        this.repo = repo;
        this.retencionDias = retencionDias;
    }

    /** Tarea diaria (03:00 UTC). */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void purgarDiaria() {
        purgar(OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** @return número de navegaciones a las que se les quitaron las coordenadas */
    @Transactional
    public int purgar(OffsetDateTime ahora) {
        if (retencionDias < 1) {
            log.warn("[Purga] eroutes.navegacion.retencion-dias={} no es válido (mínimo 1): no se purga",
                    retencionDias);
            return 0;
        }
        int n = repo.purgarCoordenadasAnterioresA(ahora.minusDays(retencionDias));
        if (n > 0) {
            log.info("[Purga] Coordenadas de origen/destino eliminadas en {} navegación(es) con más de {} días",
                    n, retencionDias);
        }
        return n;
    }
}
