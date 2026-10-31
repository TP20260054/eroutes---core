package tp20260054.eroutes.mapeado.dto;

public record CoberturaDto(String zona, double minLat, double minLng, double maxLat,
		double maxLng, Coordenada centro, long totalTramos, long totalNodos) {
}
