package tp20260054.eroutes.mapeado.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

@Getter
@Setter
@Entity
@Table(name = "nodos")
public class Nodo {

	@Id
	private Long id;

	@Column(columnDefinition = "geometry(Point,4326)", nullable = false)
	private Point geom;

	@Column(name = "control")
	private String control;
}
