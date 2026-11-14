package tp20260054.eroutes.mapeado.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tp20260054.eroutes.routing.service.GrafoNoDisponibleException;
import tp20260054.eroutes.routing.service.MismoNodoException;
import tp20260054.eroutes.routing.service.PuntoFueraDeCoberturaException;
import tp20260054.eroutes.routing.service.SinCaminoException;
import tp20260054.eroutes.usuarios.CorreoDuplicadoException;
import tp20260054.eroutes.usuarios.ContrasenaIncorrectaException;
import tp20260054.eroutes.usuarios.CredencialesInvalidasException;
import tp20260054.eroutes.usuarios.MaximoVehiculosException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalidBbox(IllegalArgumentException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Parámetros inválidos", exception.getMessage());
	}

	@ExceptionHandler({
			MethodArgumentTypeMismatchException.class,
			HandlerMethodValidationException.class,
			ConstraintViolationException.class,
			MethodArgumentNotValidException.class
	})
	ProblemDetail invalidParameters(Exception exception) {
		return problem(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
				"No se pudieron convertir o validar los parámetros de la solicitud");
	}

	@ExceptionHandler(ContrasenaIncorrectaException.class)
	ProblemDetail contrasenaIncorrecta(ContrasenaIncorrectaException exception) {
		return problem(HttpStatus.BAD_REQUEST, "Contraseña incorrecta", exception.getMessage());
	}

	@ExceptionHandler(CredencialesInvalidasException.class)
	ProblemDetail credencialesInvalidas(CredencialesInvalidasException exception) {
		return problem(HttpStatus.UNAUTHORIZED, "Credenciales inválidas",
				exception.getReason() != null ? exception.getReason() : exception.getMessage());
	}

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail notFound(RecursoNoEncontradoException exception) {
		return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage());
	}

	@ExceptionHandler(SinCaminoException.class)
	ProblemDetail sinCamino(SinCaminoException exception) {
		return problem(HttpStatus.NOT_FOUND, "Sin camino disponible", exception.getMessage());
	}

	@ExceptionHandler({PuntoFueraDeCoberturaException.class, MismoNodoException.class})
	ProblemDetail fueraDeCobertura(RuntimeException exception) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Punto fuera de la zona cubierta",
				exception.getMessage());
	}

	@ExceptionHandler(GrafoNoDisponibleException.class)
	ProblemDetail grafoNoDisponible(GrafoNoDisponibleException exception) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", exception.getMessage());
	}

	@ExceptionHandler(CorreoDuplicadoException.class)
	ProblemDetail correoDuplicado(CorreoDuplicadoException exception) {
		return problem(HttpStatus.CONFLICT, "Correo duplicado", exception.getMessage());
	}

	@ExceptionHandler(MaximoVehiculosException.class)
	ProblemDetail maximoVehiculos(MaximoVehiculosException exception) {
		return problem(HttpStatus.CONFLICT, "Límite de vehículos alcanzado", exception.getMessage());
	}

	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail responseStatus(ResponseStatusException exception) {
		HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
		return problem(status, status.getReasonPhrase(), exception.getReason());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception exception) {
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado al procesar la solicitud");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
