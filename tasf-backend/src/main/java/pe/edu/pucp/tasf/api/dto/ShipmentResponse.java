package pe.edu.pucp.tasf.api.dto;

import java.time.Instant;
import java.util.List;

public record ShipmentResponse(
    String id,
    String origin,
    String destination,
    int quantity,
    Instant deadline,
    ShipmentStatus status,
    String currentLocation,
    List<String> route,
    int currentRouteIndex,
    Instant createdAt,
    Instant updatedAt
) {}
