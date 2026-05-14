package pe.edu.pucp.tasf.api.dto;

import java.util.List;

public record CollapseEventResponse(
    String airportId,
    String reason,
    String severity,
    List<String> cancelledFlightIds,
    boolean resolved
) {}
