package pe.edu.pucp.tasf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa una solución completa del problema: un conjunto de
 * {@link RouteAssignment} (una por cada {@link ShipmentRequest}).
 *
 * La función objetivo principal es minimizar la cantidad de maletas que
 * se entregan fuera del plazo. Como desempate se usa el retraso acumulado
 * y la penalización por infactibilidades (capacidades excedidas o vuelos
 * cancelados).
 */
public class Solution {

    /** Conjunto ordenado de asignaciones de ruta. */
    private final List<RouteAssignment> routes;

    /** Métricas cacheadas de la solución. */
    private Metrics metrics;

    public Solution() {
        this.routes = new ArrayList<>();
    }

    public Solution(List<RouteAssignment> routes) {
        this.routes = new ArrayList<>(routes);
    }

    public void addRoute(RouteAssignment route) {
        this.routes.add(route);
        this.metrics = null;
    }

    /** Objetivo principal: número total de maletas entregadas tarde. */
    public int getLateCount() {
        return metrics().lateCount();
    }

    /** Métrica secundaria: retraso total acumulado (ponderado por cantidad). */
    public double getTotalDelay() {
        return metrics().totalDelay();
    }

    /** Suma de maletas en todas las solicitudes (indicador del tamaño del problema). */
    public int getTotalSuitcases() {
        return metrics().totalSuitcases();
    }

    /** Maletas entregadas (en ruta factible y a tiempo). */
    public int getDeliveredCount() {
        return metrics().deliveredCount();
    }

    /** Maletas no entregadas (ruta vacía o infactible). */
    public int getUndeliveredCount() {
        return metrics().undeliveredCount();
    }

    /** Exceso de capacidad en vuelos (suma de unidades sobre el límite). */
    public int getCapacityOverflow() {
        return metrics().capacityOverflow();
    }

    /** Exceso de capacidad en almacenes (actualmente modelado como 0; extensible). */
    public int getWarehouseOverflow() {
        return metrics().warehouseOverflow();
    }

    /** Número total de solicitudes (pedidos). */
    public int getTotalRequests() {
        return routes.size();
    }

    /**
     * Fitness (menor es mejor). Combina la cantidad de maletas tardías
     * (principal) con el retraso acumulado (desempate) y agrega una
     * penalización fuerte si la ruta es infactible.
     *
     * Valores por defecto (pueden reconfigurarse desde TabuSearchConfig):
     *   - PENALTY_LATE       = 1000  por maleta tardía
     *   - PENALTY_DELAY      = 100   por día de retraso por maleta
     *   - PENALTY_INFEASIBLE = 5000  por maleta en ruta infactible
     */
    public double getFitness() {
        return metrics().fitness();
    }

    /** Copia profunda: duplica cada RouteAssignment para aislar estados. */
    public Solution copy() {
        List<RouteAssignment> copied = new ArrayList<>();
        for (RouteAssignment ra : routes) {
            copied.add(ra.copy());
        }
        return new Solution(copied);
    }

    // ----------------- Getters -----------------
    public List<RouteAssignment> getRoutes() { return Collections.unmodifiableList(routes); }
    public int size() { return routes.size(); }
    public RouteAssignment getRoute(int index) { return routes.get(index); }

    public void setRoute(int index, RouteAssignment route) {
        routes.set(index, route);
        this.metrics = null;
    }

    @Override
    public String toString() {
        return String.format("Solución [solicitudes=%d, maletas=%d, tarde=%d, fitness=%.2f]",
                routes.size(), getTotalSuitcases(), getLateCount(), getFitness());
    }

    private Metrics metrics() {
        if (metrics == null) {
            metrics = computeMetrics();
        }
        return metrics;
    }

    private Metrics computeMetrics() {
        final double penaltyUndelivered = getPenalty("tasf.penaltyUndelivered", 20000.0);
        final double penaltyLate = getPenalty("tasf.penaltyLate", 1000.0);
        final double penaltyDelay = getPenalty("tasf.penaltyDelay", 100.0);
        final double penaltyCancelled = getPenalty("tasf.penaltyCancelled", 20000.0);
        final double penaltyCapacity = getPenalty("tasf.penaltyCapacity", 5000.0);
        final double penaltyWarehouse = getPenalty("tasf.penaltyWarehouse", 500.0);

        int lateCount = 0;
        int undeliveredCount = 0;
        int deliveredCount = 0;
        int totalSuitcases = 0;
        double totalDelay = 0.0;
        double fitness = 0.0;

        Map<Flight, Integer> flightLoads = new HashMap<>();
        Map<Airport, List<StockEvent>> stockEvents = new HashMap<>();

        for (RouteAssignment route : routes) {
            ShipmentRequest request = route.getRequest();
            int qty = request.getQuantity();
            totalSuitcases += qty;

            if (!reachesDestination(route)) {
                undeliveredCount += qty;
                lateCount += qty;
                fitness += penaltyUndelivered * qty;
                addInterval(
                    stockEvents,
                    request.getOrigin(),
                    request.getCreationTime(),
                    request.getCreationTime() + request.getDeadline(),
                    qty
                );
                continue;
            }

            deliveredCount += qty;

            if (!route.isOnTime()) {
                lateCount += qty;
                totalDelay += route.getDelay() * qty;
                fitness += penaltyLate * qty;
                fitness += penaltyDelay * route.getDelay() * qty;
            }

            if (usesCancelledFlight(route)) {
                fitness += penaltyCancelled * qty;
            }

            List<Flight> flights = route.getFlights();
            for (Flight flight : flights) {
                flightLoads.merge(flight, qty, Integer::sum);
            }

            Flight first = flights.get(0);
            addInterval(
                stockEvents,
                request.getOrigin(),
                request.getCreationTime(),
                first.getDepartureTime(),
                qty
            );

            for (int i = 0; i < flights.size() - 1; i++) {
                Flight current = flights.get(i);
                Flight next = flights.get(i + 1);
                addInterval(
                    stockEvents,
                    current.getDestination(),
                    current.getArrivalTime(),
                    next.getDepartureTime(),
                    qty
                );
            }
        }

        int capacityOverflow = 0;
        for (Map.Entry<Flight, Integer> entry : flightLoads.entrySet()) {
            int overflow = entry.getValue() - entry.getKey().getCapacity();
            if (overflow > 0) {
                capacityOverflow += overflow;
            }
        }

        int warehouseOverflow = 0;
        for (Map.Entry<Airport, List<StockEvent>> entry : stockEvents.entrySet()) {
            Airport airport = entry.getKey();
            List<StockEvent> events = entry.getValue();
            events.sort(
                Comparator
                    .comparingDouble(StockEvent::time)
                    .thenComparingInt(StockEvent::delta)
            );

            int current = 0;
            int peak = 0;
            for (StockEvent event : events) {
                current += event.delta();
                if (current > peak) {
                    peak = current;
                }
            }
            int overflow = peak - airport.getWarehouseCapacity();
            if (overflow > 0) {
                warehouseOverflow += overflow;
            }
        }

        fitness += penaltyCapacity * capacityOverflow;
        fitness += penaltyWarehouse * warehouseOverflow;

        return new Metrics(
            lateCount,
            undeliveredCount,
            deliveredCount,
            totalSuitcases,
            totalDelay,
            capacityOverflow,
            warehouseOverflow,
            fitness
        );
    }

    private static double getPenalty(String key, double defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean reachesDestination(RouteAssignment route) {
        List<Flight> flights = route.getFlights();
        if (flights.isEmpty()) {
            return false;
        }
        Airport reached = flights.get(flights.size() - 1).getDestination();
        return reached.equals(route.getRequest().getDestination());
    }

    private static boolean usesCancelledFlight(RouteAssignment route) {
        for (Flight f : route.getFlights()) {
            if (f.isCancelled()) {
                return true;
            }
        }
        return false;
    }

    private static void addInterval(
        Map<Airport, List<StockEvent>> stockEvents,
        Airport airport,
        double start,
        double end,
        int qty
    ) {
        if (airport == null || end <= start) {
            return;
        }
        List<StockEvent> events = stockEvents.computeIfAbsent(
            airport,
            key -> new ArrayList<>()
        );
        events.add(new StockEvent(start, qty));
        events.add(new StockEvent(end, -qty));
    }

    private record StockEvent(double time, int delta) {}

    private record Metrics(
        int lateCount,
        int undeliveredCount,
        int deliveredCount,
        int totalSuitcases,
        double totalDelay,
        int capacityOverflow,
        int warehouseOverflow,
        double fitness
    ) {}
}
