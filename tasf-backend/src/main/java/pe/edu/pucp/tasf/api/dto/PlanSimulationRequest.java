package pe.edu.pucp.tasf.api.dto;

import java.time.LocalDate;

public record PlanSimulationRequest(
    Integer startDay,
    LocalDate startDate,
    Integer days,
    Integer maxIterations,
    Integer maxHops,
    Long timeLimitMs,
    Long seed
) {}
