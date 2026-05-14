package pe.edu.pucp.tasf.api.dto;

public record FlightResponse(
    String id,
    String origin,
    String destination,
    int departureTime,
    double duration,
    int capacity,
    int currentLoad
) {}
