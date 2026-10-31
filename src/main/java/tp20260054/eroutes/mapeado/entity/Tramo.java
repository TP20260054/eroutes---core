package tp20260054.eroutes.mapeado.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.LineString;

@Getter
@Setter
@Entity
@Table(name = "tramos")
public class Tramo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "osm_way_id")
	private Long osmWayId;

	@Column(name = "nodo_origen", nullable = false)
	private Long nodoOrigen;

	@Column(name = "nodo_destino", nullable = false)
	private Long nodoDestino;

	@Column(columnDefinition = "geometry(LineString,4326)", nullable = false)
	private LineString geom;

	@Column(name = "longitud_m", nullable = false)
	private double longitudM;

	@Column(name = "tipo_via")
	private String tipoVia;

	@Column(name = "velocidad_max")
	private Integer velocidadMax;

	@Column(name = "es_ciclovia", nullable = false)
	private boolean esCiclovia;

	@Column(nullable = false)
	private boolean validado;

	@Column(name = "sentido_unico", nullable = false)
	private boolean sentidoUnico;
}
