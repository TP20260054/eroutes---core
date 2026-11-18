package tp20260054.eroutes.recalculo;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Almacén en memoria de sesiones de navegación activas.
 *
 * <p>Las sesiones expiran por inactividad ({@code sesion-ttl-min}). Un job
 * periódico cada 60 s elimina las expiradas. El acceso a una sesión ajena
 * (usuarioId distinto) devuelve {@code Optional.empty()}, y la capa de servicio
 * lanza {@link NavegacionNoEncontradaException} → 404.
 */
@Component
public class SesionNavegacionStore {

    private static final Logger log = LoggerFactory.getLogger(SesionNavegacionStore.class);

    private final ConcurrentHashMap<Long, SesionNavegacion> sesiones = new ConcurrentHashMap<>();

    @Value("${eroutes.recalculo.sesion-ttl-min:120}")
    private long sesionTtlMin;

    // -------------------------------------------------------------------------

    public void guardar(SesionNavegacion sesion) {
        sesiones.put(sesion.navegacionId(), sesion);
    }

    /**
     * Busca la sesión verificando que pertenece al usuario dado.
     * Devuelve {@code Optional.empty()} si no existe o si es de otro usuario.
     * Actualiza el timestamp de último acceso cuando encuentra la sesión.
     */
    public Optional<SesionNavegacion> obtener(long navegacionId, long usuarioId) {
        SesionNavegacion sesion = sesiones.get(navegacionId);
        if (sesion == null || sesion.usuarioId() != usuarioId) return Optional.empty();
        sesion.tocarAcceso();
        return Optional.of(sesion);
    }

    public void eliminar(long navegacionId) {
        sesiones.remove(navegacionId);
    }

    // -------------------------------------------------------------------------

    /** Elimina sesiones sin actividad durante más de {@code sesionTtlMin} minutos. */
    @Scheduled(fixedDelay = 60_000)
    public void purgarExpiradas() {
        Instant limite = Instant.now().minusSeconds(sesionTtlMin * 60);
        int antes = sesiones.size();
        sesiones.entrySet().removeIf(e -> e.getValue().ultimoAcceso().isBefore(limite));
        int eliminadas = antes - sesiones.size();
        if (eliminadas > 0) {
            log.info("[Sesiones] Purgadas {} sesión(es) expirada(s)", eliminadas);
        }
    }
}
