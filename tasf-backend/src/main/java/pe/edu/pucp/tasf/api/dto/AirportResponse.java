package pe.edu.pucp.tasf.api.dto;

public record AirportResponse(
    String id,
    String code,
    String city,
    String continent,
    int storageCapacity,
    int currentStorage,
    double latitude,
    double longitude
) {}
