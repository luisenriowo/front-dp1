package pe.edu.pucp.tasf.service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import pe.edu.pucp.tasf.service.state.SimulationStatus;
import org.springframework.stereotype.Service;
import pe.edu.pucp.tasf.algorithm.TabuSearchConfig;
import pe.edu.pucp.tasf.algorithm.TabuSearchSolver;
import pe.edu.pucp.tasf.api.dto.CollapseEventResponse;
import pe.edu.pucp.tasf.api.dto.FlightResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentStatus;
import pe.edu.pucp.tasf.api.dto.SimulationMetricsResponse;
import pe.edu.pucp.tasf.api.dto.SimulationStateResponse;
import pe.edu.pucp.tasf.io.AirportsLoader;
import pe.edu.pucp.tasf.io.EnviosDataLoader;
import pe.edu.pucp.tasf.model.Airport;
import pe.edu.pucp.tasf.model.Flight;
import pe.edu.pucp.tasf.model.LogisticsNetwork;
import pe.edu.pucp.tasf.model.RouteAssignment;
import pe.edu.pucp.tasf.model.ShipmentRequest;
import pe.edu.pucp.tasf.model.Solution;
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
    private Map<String, Integer> gmtMap = Map.of();

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
            this.gmtMap = Map.copyOf(airportsLoader.getGmtMap());
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
        network.resetLoads();
    }

    public synchronized SimulationStateResponse planWithTabuSearch(
        Integer startDay,
        LocalDate startDate,
        int days,
        int maxIterations,
        int maxHops,
        long timeLimitMs,
        long seed
    ) {
        EnviosDataLoader loader = new EnviosDataLoader();
        loader.setGmtMap(gmtMap);
        try {
            if (startDate != null) {
                loader.loadFromFolder(Path.of("_envios_preliminar_"), startDate, days);
            } else {
                loader.loadFromFolder(Path.of("_envios_preliminar_"), Math.max(0, startDay == null ? 0 : startDay), days);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load shipment demand files", e);
        }

        List<ShipmentRequest> requests = loader.resolveAirports(network);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No shipment demands found for the selected range");
        }

        TabuSearchConfig config = new TabuSearchConfig()
            .maxIterations(maxIterations)
            .tabuTenure(20)
            .maxHops(maxHops)
            .neighborhoodRatio(0.30)
            .criticalNeighborhoodShare(0.70)
            .criticalRefreshInterval(20)
            .criticalSlackDays(0.10)
            .bottleneckUtilization(0.90)
            .stagnationIterations(1400)
            .timeLimitMs(timeLimitMs)
            .seed(seed);

        TabuSearchSolver solver = new TabuSearchSolver(network, requests, config);
        Solution solution = solver.solve();

        LocalDate baseDate = loader.getBaseDate();
        List<ShipmentResponse> plannedShipments = toShipmentResponses(solution, baseDate);
        List<FlightResponse> plannedFlights = toUsedFlightResponses(solution);

        shipments.clear();
        shipmentSequence.set(1);

        return new SimulationStateResponse(
            Math.max(1, startDay == null ? 1 : startDay + 1),
            0,
            network.getAirports().stream().map(this::toAirportResponse).toList(),
            plannedFlights,
            plannedShipments,
            toSolutionMetrics(solution)
        );
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

    private pe.edu.pucp.tasf.api.dto.AirportResponse toAirportResponse(Airport airport) {
        return new pe.edu.pucp.tasf.api.dto.AirportResponse(
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

    private List<ShipmentResponse> toShipmentResponses(Solution solution, LocalDate baseDate) {
        Instant now = Instant.now();
        List<ShipmentResponse> result = new ArrayList<>();
        for (RouteAssignment route : solution.getRoutes()) {
            ShipmentRequest request = route.getRequest();
            boolean feasible = route.isFeasible();
            ShipmentStatus shipmentStatus = !feasible
                ? ShipmentStatus.NO_ROUTE
                : route.isOnTime() ? ShipmentStatus.DELIVERED : ShipmentStatus.DELIVERED_LATE;
            Instant createdAt = toInstant(baseDate, request.getCreationTime());
            Instant deadline = toInstant(baseDate, request.getCreationTime() + request.getDeadline());
            result.add(new ShipmentResponse(
                request.getId(),
                request.getOrigin().getCode(),
                request.getDestination().getCode(),
                request.getQuantity(),
                deadline,
                shipmentStatus,
                feasible ? request.getDestination().getCode() : request.getOrigin().getCode(),
                toAirportRoute(request.getOrigin(), route.getFlights()),
                feasible ? route.getFlights().size() : 0,
                createdAt,
                now
            ));
        }
        return result;
    }

    private List<FlightResponse> toUsedFlightResponses(Solution solution) {
        Map<String, Flight> flightsById = new LinkedHashMap<>();
        Map<String, Integer> loadsById = new HashMap<>();
        for (RouteAssignment route : solution.getRoutes()) {
            int quantity = route.getRequest().getQuantity();
            for (Flight flight : route.getFlights()) {
                flightsById.putIfAbsent(flight.getId(), flight);
                loadsById.merge(flight.getId(), quantity, Integer::sum);
            }
        }
        return flightsById.values().stream()
            .map(flight -> new FlightResponse(
                flight.getId(),
                flight.getOrigin().getCode(),
                flight.getDestination().getCode(),
                ((int) Math.round((flight.getDepartureTime() % 1.0) * 24)) % 24,
                flight.getTransitTime(),
                flight.getCapacity(),
                loadsById.getOrDefault(flight.getId(), 0)
            ))
            .toList();
    }

    private SimulationMetricsResponse toSolutionMetrics(Solution solution) {
        int deliveredOnTime = 0;
        int deliveredLate = 0;
        int pending = 0;
        double transitDays = 0.0;
        int delivered = 0;

        for (RouteAssignment route : solution.getRoutes()) {
            if (!route.isFeasible()) {
                pending++;
                continue;
            }
            delivered++;
            transitDays += route.getTotalTransitTime();
            if (route.isOnTime()) {
                deliveredOnTime++;
            } else {
                deliveredLate++;
            }
        }

        Map<String, Double> warehouseUtilization = network.getAirports().stream()
            .collect(java.util.stream.Collectors.toMap(
                Airport::getCode,
                a -> a.getWarehouseCapacity() > 0
                    ? (double) a.getCurrentStock() / a.getWarehouseCapacity()
                    : 0.0
            ));

        return new SimulationMetricsResponse(
            solution.getTotalRequests(),
            deliveredOnTime,
            deliveredLate,
            0,
            pending,
            delivered > 0 ? transitDays / delivered : 0.0,
            warehouseUtilization
        );
    }

    private Instant toInstant(LocalDate baseDate, double dayTime) {
        long seconds = Math.round(dayTime * 86400.0);
        return baseDate.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(seconds);
    }
}
