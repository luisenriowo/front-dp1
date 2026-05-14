package pe.edu.pucp.tasf.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pe.edu.pucp.tasf.api.dto.AirportResponse;
import pe.edu.pucp.tasf.api.dto.FlightResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentStatus;
import pe.edu.pucp.tasf.api.dto.SimulationMetricsResponse;
import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Flight;
import pe.edu.pucp.tasf.service.state.ShipmentRecord;

@Component
public class ApiMapper {

    public AirportResponse toAirportResponse(Airport airport) {
        return new AirportResponse(
            airport.getCode(),
            airport.getCode(),
            airport.getCity(),
            airport.getContinent().name(),
            airport.getWarehouseCapacity(),
            airport.getCurrentStock(),
            airport.getLatitude(),
            airport.getLongitude()
        );
    }

    public FlightResponse toFlightResponse(Flight flight) {
        int departureHour = (int) Math.round(flight.getDepartureTime() * 24) % 24;
        return new FlightResponse(
            flight.getId(),
            flight.getOrigin().getCode(),
            flight.getDestination().getCode(),
            departureHour,
            flight.getTransitTime(),
            flight.getCapacity(),
            flight.getAssignedLoad()
        );
    }

    public ShipmentResponse toShipmentResponse(ShipmentRecord record) {
        return new ShipmentResponse(
            record.id(),
            record.origin(),
            record.destination(),
            record.quantity(),
            record.deadline(),
            record.status(),
            record.currentLocation(),
            record.route(),
            record.currentRouteIndex(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    public SimulationMetricsResponse toMetricsResponse(List<ShipmentRecord> shipments, List<Airport> airports) {
        int total = shipments.size();
        int deliveredOnTime = (int) shipments.stream().filter(s -> s.status() == ShipmentStatus.DELIVERED).count();
        int deliveredLate = (int) shipments.stream().filter(s -> s.status() == ShipmentStatus.DELIVERED_LATE).count();
        int inTransit = (int) shipments.stream().filter(s -> s.status() == ShipmentStatus.IN_TRANSIT).count();
        int pending = (int) shipments.stream().filter(s -> s.status() == ShipmentStatus.PENDING).count();

        double avgSeconds = shipments.stream()
            .map(ShipmentRecord::deliveryDuration)
            .filter(d -> d != null && !d.isNegative() && !d.isZero())
            .mapToLong(Duration::toSeconds)
            .average()
            .orElse(0.0);
        double averageDeliveryTimeHours = avgSeconds / 3600.0;

        Map<String, Double> warehouseUtilization = airports.stream().collect(
            Collectors.toMap(
                Airport::getCode,
                a -> a.getWarehouseCapacity() > 0
                    ? (double) a.getCurrentStock() / a.getWarehouseCapacity()
                    : 0.0
            )
        );

        return new SimulationMetricsResponse(
            total,
            deliveredOnTime,
            deliveredLate,
            inTransit,
            pending,
            averageDeliveryTimeHours,
            warehouseUtilization
        );
    }

}
