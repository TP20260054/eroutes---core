package tp20260054.eroutes.navegacion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Recálculo de una navegación (Recalculation, OB2 5.1) con campos de evaluación
 * (OB2 8.3/8.4).
 *
 * <p>{@code navegacion} es la única relación JPA (padre-hijo dentro del módulo, con
 * ON DELETE CASCADE en BD). {@code estadoEntrada} y {@code pesosUsados} son JSONB
 * mapeados con {@link JsonNode}. {@code accion} es un código 0/1/2 (SMALLINT, sin
 * enum) y {@code configuracion} es un String libre (la columna en recalculos NO
 * tiene CHECK, a diferencia de navegaciones).
 */
@Getter
@Setter
@Entity
@Table(name = "recalculos")
public class Recalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "navegacion_id", nullable = false)
    private Navegacion navegacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DisparadorRecalculo disparador;

    /** Código de acción 0/1/2 (SMALLINT con CHECK en BD). */
    @Column(nullable = false)
    private Short accion;

    /** Configuración usada; sin CHECK en recalculos, por eso es String libre. */
    @Column(nullable = false, length = 10)
    private String configuracion;

    /** Estado de entrada: vector + crudos, SIN coordenadas. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estado_entrada", nullable = false)
    private JsonNode estadoEntrada;

    @Column(name = "tiempo_inferencia_ms")
    private Double tiempoInferenciaMs;

    @Column(name = "tiempo_calculo_ms")
    private Double tiempoCalculoMs;

    @Column(name = "tiempo_total_servidor_ms")
    private Double tiempoTotalServidorMs;

    @Column(name = "policy_version", nullable = false, length = 60)
    private String policyVersion;

    /** Si el recálculo usó la ruta de respaldo. */
    @Column(name = "uso_respaldo", nullable = false)
    private boolean usoRespaldo;

    @Column(name = "motivo_respaldo", length = 30)
    private String motivoRespaldo;

    /** Pesos usados (puede ser null si se usaron los por defecto). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pesos_usados")
    private JsonNode pesosUsados;

    @Column(name = "ruta_anterior_bloqueada")
    private Boolean rutaAnteriorBloqueada;

    /** Costo de la ruta anterior con la misma función que {@code costoNuevo}; NULL si estaba bloqueada. */
    @Column(name = "costo_anterior")
    private Double costoAnterior;

    @Column(name = "costo_nuevo")
    private Double costoNuevo;
    /** Tiempo (s) de la ruta anterior desde la posición actual, con la misma fórmula que el nuevo. */
    @Column(name = "costo_anterior_tiempo_s")
    private Double costoAnteriorTiempoS;
    /** Tiempo (s) de la ruta nueva, con la misma fórmula de tiempo que la anterior. */
    @Column(name = "costo_nuevo_tiempo_s")
    private Double costoNuevoTiempoS;

    /** Etiqueta para OE4 (ver 3.6). */
    @Column(name = "innecesario")
    private Boolean innecesario;

    @Column(name = "exito", nullable = false)
    private boolean exito;

    @Column(name = "mensaje", length = 255)
    private String mensaje;

    @Column(name = "creado_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime creadoEn;
}
