package pe.edu.pucp.tasf.service.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import pe.edu.pucp.tasf.api.dto.ShipmentStatus;

public record ShipmentRecord(
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
    Instant updatedAt,
    Duration deliveryDuration
) {}
