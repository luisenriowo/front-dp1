package pe.edu.pucp.tasf.api.dto;

import java.util.List;

public record SimulationStateResponse(
    int day,
    int timeOfDay,
    List<AirportResponse> airports,
    List<FlightResponse> flights,
    List<ShipmentResponse> shipments,
    SimulationMetricsResponse metrics
) {}
