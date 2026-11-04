package tp20260054.eroutes.mapeado.validation;

public final class BboxValidator {

	private BboxValidator() {
	}

	public static void validate(double minLat, double minLng, double maxLat, double maxLng) {
		if (!Double.isFinite(minLat) || !Double.isFinite(minLng)
				|| !Double.isFinite(maxLat) || !Double.isFinite(maxLng)
				|| minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180
				|| minLat >= maxLat || minLng >= maxLng
				|| maxLat - minLat > 0.1 || maxLng - minLng > 0.1) {
			throw new IllegalArgumentException(
					"El bounding box debe tener límites válidos, ordenados y lados de máximo 0.1 grados");
		}
	}
}
