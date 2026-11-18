package tp20260054.eroutes.eventos;

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
import org.locationtech.jts.geom.Point;

/**
 * Evento vial reportado sobre un tramo: precursor de Report / TEMPORAL_BLOCK (OB2 5.1).
 *
 * <p>El {@code creadoEn} se fija al insertar (igual que en {@code Usuario}/{@code Vehiculo});
 * el DEFAULT now() de la BD es solo respaldo.
 */
@Getter
@Setter
@Entity
@Table(name = "eventos_viales")
public class EventoVial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEventoVial tipo;

    /** Gravedad normalizada en [0, 1] (CHECK en BD). */
    @Column(nullable = false)
    private double gravedad;

    /** Tramo afectado (FK a tramos(id), sin relación JPA: se opera por id). */
    @Column(name = "tramo_id", nullable = false)
    private Long tramoId;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point geom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoEventoVial estado;

    /** Usuario que reporta el evento (FK a usuarios(id), sin relación JPA). */
    @Column(name = "autor_usuario_id", nullable = false)
    private Long autorUsuarioId;

    @Column(length = 255)
    private String motivo;

    @Column(name = "creado_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime creadoEn;

    @Column(name = "expira_en", nullable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime expiraEn;

    @Column(name = "resuelto_en",
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime resueltoEn;
}
