package tp20260054.eroutes.usuarios;

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

@Getter
@Setter
@Entity
@Table(name = "vehiculos")
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(length = 60)
    private String alias;

    @Column(length = 60)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoVehiculo tipo;

    @Column(name = "velocidad_maxima_kmh", nullable = false)
    private int velocidadMaximaKmh;

    @Column(length = 10)
    private String placa;

    @Column(name = "creado_en", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime actualizadoEn;
}
