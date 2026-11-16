package tp20260054.eroutes.navegacion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Sesión de navegación (Navigation, OB2 5.1).
 *
 * <p>{@code usuarioId} y {@code vehiculoId} se guardan como id plano (sin relación JPA),
 * igual que {@code tramos.nodo_origen/nodo_destino}. {@code pesosUsados} se almacena
 * como JSONB usando {@link JsonNode} (mapeo JSON de Hibernate, sin doble codificación).
 *
 * <p>Retención OB2 9.1: {@code origen} y {@code destino} se conservan hasta 30 días
 * tras el cierre del OE4; la purga no se implementa aquí.
 */
@Getter
@Setter
@Entity
@Table(name = "navegaciones")
public class Navegacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Usuario que navegó (FK a usuarios(id), sin relación JPA). */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Vehículo usado (FK a vehiculos(id), sin relación JPA). */
    @Column(name = "vehiculo_id", nullable = false)
    private Long vehiculoId;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point origen;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EtiquetaNavegacion etiqueta;

    @Column(name = "distancia_m", nullable = false)
    private double distanciaM;

    @Column(name = "tiempo_estimado_s", nullable = false)
    private double tiempoEstimadoS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pesos_usados", nullable = false)
    private JsonNode pesosUsados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConfiguracionNavegacion configuracion;

    @Column(name = "creado_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime creadoEn;
}
