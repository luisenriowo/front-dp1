package pe.edu.pucp.tasf.api.dto;

import java.util.Map;

public record SimulationMetricsResponse(
    int totalShipments,
    int deliveredOnTime,
    int deliveredLate,
    int inTransit,
    int pending,
    double averageDeliveryTime,
    Map<String, Double> warehouseUtilization
) {}
