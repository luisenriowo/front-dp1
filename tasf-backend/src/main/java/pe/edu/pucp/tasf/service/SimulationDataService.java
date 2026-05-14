package pe.edu.pucp.tasf.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import pe.edu.pucp.tasf.service.state.SimulationStatus;
import org.springframework.stereotype.Service;
import pe.edu.pucp.tasf.api.dto.CollapseEventResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentStatus;
import pe.edu.pucp.tasf.io.AirportsLoader;
import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Flight;
import pe.edu.pucp.tasf.model.LogisticsNetwork;
import pe.edu.pucp.tasf.service.exception.CapacityExceededException;
import pe.edu.pucp.tasf.service.exception.NoFeasibleRouteException;
import pe.edu.pucp.tasf.service.exception.ResourceNotFoundException;
import pe.edu.pucp.tasf.service.state.ShipmentRecord;
import pe.edu.pucp.tasf.util.RealNetworkBuilder;

@Service
public class SimulationDataService {

    private final AtomicLong shipmentSequence = new AtomicLong(1);
    private final AtomicReference<SimulationStatus> status = new AtomicReference<>(SimulationStatus.STOPPED);
    private final Map<String, ShipmentRecord> shipments = new ConcurrentHashMap<>();
    private final List<CollapseEventResponse> collapseEvents = new ArrayList<>();

    private LogisticsNetwork network;

    @PostConstruct
    void init() {
        AirportsLoader airportsLoader = new AirportsLoader();
        try {
            airportsLoader.loadFromFile(Path.of("aeropuertos.txt"));
            RealNetworkBuilder builder = new RealNetworkBuilder(42);
            this.network = builder.build(
                airportsLoader.getAirports(),
                Path.of("planes_vuelo.txt"),
                airportsLoader.getGmtMap()
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize simulation data from local files", e);
        }
    }

    public Collection<Airport> getAirports() {
        return network.getAirports();
    }

    public List<Flight> getFlights() {
        return network.getFlights();
    }

    public List<ShipmentRecord> getShipments() {
        return shipments.values().stream()
            .sorted(Comparator.comparing(ShipmentRecord::createdAt))
            .toList();
    }

    public ShipmentRecord getShipmentById(String id) {
        ShipmentRecord record = shipments.get(id);
        if (record == null) {
            throw new ResourceNotFoundException("Shipment not found: " + id);
        }
        return record;
    }

    public ShipmentRecord createShipment(String originCode, String destinationCode, int quantity, Instant deadline) {
        Airport origin = requireAirport(originCode);
        Airport destination = requireAirport(destinationCode);
        if (origin.getCode().equals(destination.getCode())) {
            throw new IllegalArgumentException("Origin and destination must be different");
        }

        List<Flight> bestRoute = findBestRoute(origin, destination, quantity);
        if (bestRoute.isEmpty()) {
            throw new NoFeasibleRouteException(
                "No feasible route found between " + origin.getCode() + " and " + destination.getCode()
            );
        }

        for (Flight flight : bestRoute) {
            if (!flight.canAssign(quantity)) {
                throw new CapacityExceededException(
                    "Flight capacity exceeded on " + flight.getId() + " for quantity " + quantity
                );
            }
        }

        for (Flight flight : bestRoute) {
            flight.assign(quantity);
        }

        String id = "s-" + shipmentSequence.getAndIncrement();
        Instant now = Instant.now();
        List<String> route = toAirportRoute(origin, bestRoute);
        ShipmentRecord record = new ShipmentRecord(
            id,
            origin.getCode(),
            destination.getCode(),
            quantity,
            deadline,
            ShipmentStatus.PENDING,
            origin.getCode(),
            route,
            0,
            now,
            now,
            null
        );
        shipments.put(id, record);
        return record;
    }

    public List<CollapseEventResponse> getCollapseEvents() {
        return List.copyOf(collapseEvents);
    }

    public int getSimulationDay() {
        return 1;
    }

    public int getSimulationTimeOfDay() {
        return 0;
    }

    public SimulationStatus getStatus() {
        return status.get();
    }

    public void start() {
        status.set(SimulationStatus.RUNNING);
    }

    public void pause() {
        status.set(SimulationStatus.PAUSED);
    }

    public void reset() {
        shipments.clear();
        shipmentSequence.set(1);
        status.set(SimulationStatus.STOPPED);
    }

    public Airport getAirportByCode(String code) {
        return requireAirport(code);
    }

    public Flight getFlightById(String id) {
        return network.getFlights().stream()
            .filter(f -> f.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Flight not found: " + id));
    }

    private Airport requireAirport(String code) {
        Airport airport = network.getAirport(code.toUpperCase());
        if (airport == null) {
            throw new ResourceNotFoundException("Airport not found: " + code);
        }
        return airport;
    }

    private List<Flight> findBestRoute(Airport origin, Airport destination, int quantity) {
        List<List<Flight>> candidates = network.findRoutes(origin, destination, 0.0, 4, 100);
        List<Flight> best = new ArrayList<>();
        double bestDuration = Double.MAX_VALUE;
        for (List<Flight> candidate : candidates) {
            if (!hasCapacity(candidate, quantity)) {
                continue;
            }
            double duration = bestRouteDurationDays(candidate);
            if (duration < bestDuration) {
                bestDuration = duration;
                best = candidate;
            }
        }
        return best;
    }

    private boolean hasCapacity(List<Flight> route, int quantity) {
        for (Flight flight : route) {
            if (!flight.canAssign(quantity)) {
                return false;
            }
        }
        return true;
    }

    private double bestRouteDurationDays(List<Flight> route) {
        if (route.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double firstDeparture = route.get(0).getDepartureTime();
        double lastArrival = route.get(route.size() - 1).getArrivalTime();
        return Math.max(0.0, lastArrival - firstDeparture);
    }

    private List<String> toAirportRoute(Airport origin, List<Flight> flights) {
        List<String> route = new ArrayList<>();
        route.add(origin.getCode());
        for (Flight f : flights) {
            route.add(f.getDestination().getCode());
        }
        return route;
    }
}
