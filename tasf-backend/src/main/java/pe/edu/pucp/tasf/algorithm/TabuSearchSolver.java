package pe.edu.pucp.tasf.algorithm;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import pe.edu.pucp.tasf.model.Flight;
import pe.edu.pucp.tasf.model.LogisticsNetwork;
import pe.edu.pucp.tasf.model.RouteAssignment;
import pe.edu.pucp.tasf.model.ShipmentRequest;
import pe.edu.pucp.tasf.model.Solution;

/**
 * Tabu Search para planificación de rutas de maletas.
 *
 * Cambios principales en esta versión:
 * - Objetivo de búsqueda lexicográfico (primero maletas sin ruta, luego tardías).
 * - Vecindario crítico (sesgo hacia envíos tardíos/casi tardíos/cuello de botella).
 * - Instrumentación de estancamiento por ventana de iteraciones.
 * - Movimiento adicional BOTTLENECK_KICK para liberar capacidad en vuelos críticos.
 * - Recuperación anti-estancamiento con reinicio parcial desde best + perturbación dirigida.
 */
public class TabuSearchSolver {

    private static final int MAX_RUTAS_POR_CONSULTA = 60;
    private static final int LOG_INTERVAL = 500;
    private static final int MAX_BOTTLENECK_TARGETS = 12;
    private static final int MAX_DONOR_TRIALS = 20;
    private static final int MAX_ALT_TRIALS_PER_DONOR = 16;
    private static final int INITIAL_TOP_K = 3;
    private static final int NO_MOVE_EARLY_STOP_ITERS = 2500;
    private static final int NO_BEST_IMPROVE_STOP_FACTOR = 2;
    private static final double HOP_WEIGHT = 0.03;
    private static final double EPS = 1e-9;

    private final LogisticsNetwork network;
    private final List<ShipmentRequest> requests;
    private final TabuSearchConfig config;
    private final Random random;
    private final Map<TabuMove, Integer> tabuList;

    private final Map<ShipmentRequest, List<List<Flight>>> candidateRoutes =
        new HashMap<>();

    private Solution currentSolution;
    private Solution bestSolution;

    private double bestFitness;
    private int iteration;
    private int improvementCount;
    private long startTimeMs;
    private int lastImprovementIteration;
    private int lastAppliedMoveIteration;

    private int currentTabuTenure;

    private long currentUndelivered;
    private long currentLate;
    private double currentDelay;
    private double currentTransit;

    private long bestUndelivered;
    private long bestLate;
    private double bestDelay;
    private double bestTransit;

    private final List<Double> fitnessHistory;

    private final List<Integer> criticalIndices = new ArrayList<>();
    private final List<Integer> lateOrUndeliveredIndices = new ArrayList<>();
    private final Map<Flight, List<Integer>> flightUsers = new HashMap<>();

    public TabuSearchSolver(
        LogisticsNetwork network,
        List<ShipmentRequest> requests,
        TabuSearchConfig config
    ) {
        this.network = network;
        this.requests = requests;
        this.config = config;
        this.random = new Random(config.getSeed());
        this.tabuList = new HashMap<>();
        this.fitnessHistory = new ArrayList<>();
    }

    public Solution solve() {
        startTimeMs = System.currentTimeMillis();
        tabuList.clear();
        fitnessHistory.clear();
        improvementCount = 0;
        iteration = 0;
        lastImprovementIteration = 0;
        lastAppliedMoveIteration = 0;
        currentTabuTenure = Math.max(1, config.getTabuTenure());

        System.out.println("=== Tabu Search iniciado ===");
        System.out.printf(
            "Envíos: %d, Aeropuertos: %d, Vuelos: %d%n",
            requests.size(),
            network.getAirportCount(),
            network.getFlightCount()
        );

        candidateRoutes.clear();
        precomputeRoutes(requests);

        if (config.isFeasibilityBaseline()) {
            printFeasibilityBaseline();
        }

        currentSolution = generateInitialSolution();
        recalculateCurrentScoreFromSolution();

        bestSolution = currentSolution.copy();
        copyCurrentScoreToBest();
        bestFitness = bestSolution.getFitness();

        System.out.println("Solución inicial: " + currentSolution);
        System.out.println(
            "Objetivo inicial: " +
                formatObjective(
                    currentUndelivered,
                    currentLate,
                    currentDelay,
                    currentTransit
                )
        );
        fitnessHistory.add(bestFitness);

        refreshCriticalSets();
        DiagnosticsWindow diagnosticsWindow = new DiagnosticsWindow();

        for (
            iteration = 1;
            iteration <= config.getMaxIterations();
            iteration++
        ) {
            if (isTimeLimitReached()) {
                System.out.println(
                    "Límite de tiempo alcanzado en iteración " + iteration
                );
                break;
            }

            if (shouldRefreshCriticalSets()) {
                refreshCriticalSets();
            }

            IterationStats stats = new IterationStats();
            MoveCandidateResult bestCandidate = exploreNeighborhood(stats);

            if (bestCandidate != null) {
                boolean improvesCurrent =
                    compareScores(
                        bestCandidate.resultUndelivered,
                        bestCandidate.resultLate,
                        bestCandidate.resultDelay,
                        bestCandidate.resultTransit,
                        currentUndelivered,
                        currentLate,
                        currentDelay,
                        currentTransit
                    ) <
                    0;
                boolean improvesBest =
                    compareScores(
                        bestCandidate.resultUndelivered,
                        bestCandidate.resultLate,
                        bestCandidate.resultDelay,
                        bestCandidate.resultTransit,
                        bestUndelivered,
                        bestLate,
                        bestDelay,
                        bestTransit
                    ) <
                    0;

                if (improvesCurrent || improvesBest) {
                    applyMove(bestCandidate);
                    addToTabuList(bestCandidate);
                    stats.appliedMove = true;
                    lastAppliedMoveIteration = iteration;

                    if (isCurrentBetterThanBest()) {
                        bestSolution = currentSolution.copy();
                        copyCurrentScoreToBest();
                        bestFitness = bestSolution.getFitness();
                        improvementCount++;
                        lastImprovementIteration = iteration;
                        currentTabuTenure = Math.max(
                            config.getTabuTenure(),
                            currentTabuTenure - 1
                        );
                    }
                } else {
                    stats.rejectedNonImproving = true;
                }
            }

            int noAppliedMove = iteration - lastAppliedMoveIteration;
            if (
                noAppliedMove >= NO_MOVE_EARLY_STOP_ITERS &&
                currentUndelivered == 0 &&
                currentLate == 0 &&
                currentDelay <= EPS
            ) {
                System.out.printf(
                    "Iter %d: sin movimientos aplicados por %d iteraciones con objetivo primario óptimo; corte temprano.%n",
                    iteration,
                    noAppliedMove
                );
                break;
            }

            int noBestImprove = iteration - lastImprovementIteration;
            if (
                noBestImprove >=
                Math.max(
                    1,
                    config.getStagnationIterations() *
                    NO_BEST_IMPROVE_STOP_FACTOR
                ) &&
                bestUndelivered == 0 &&
                bestLate == 0 &&
                bestDelay <= EPS
            ) {
                System.out.printf(
                    "Iter %d: sin mejora global por %d iteraciones con objetivo primario óptimo; corte temprano.%n",
                    iteration,
                    noBestImprove
                );
                break;
            }

            int noImprove = iteration - lastImprovementIteration;
            if (noImprove >= Math.max(1, config.getStagnationIterations())) {
                recoverFromStagnation();
                stats.stagnationRecovery = true;
                lastImprovementIteration = iteration;
            }

            if (
                config.getDiversificationInterval() > 0 &&
                iteration % config.getDiversificationInterval() == 0
            ) {
                diversify();
                stats.diversified = true;
            }

            if (
                config.getIntensificationInterval() > 0 &&
                iteration % config.getIntensificationInterval() == 0
            ) {
                intensify();
                stats.intensified = true;
            }

            purgeTabuList();
            fitnessHistory.add(currentSolution.getFitness());
            diagnosticsWindow.add(stats);

            if (iteration % LOG_INTERVAL == 0) {
                long elapsedSec =
                    (System.currentTimeMillis() - startTimeMs) / 1000;
                double itPerSec =
                    elapsedSec > 0
                        ? (double) iteration / elapsedSec
                        : iteration;
                System.out.printf(
                    "Iter %d [%ds | %.1f it/s]: fitness=%.2f, mejor=%.2f, |tabú|=%d, tenure=%d%n",
                    iteration,
                    elapsedSec,
                    itPerSec,
                    currentSolution.getFitness(),
                    bestFitness,
                    tabuList.size(),
                    currentTabuTenure
                );
                System.out.printf(
                    "  actual=%s | mejor=%s%n",
                    formatObjective(
                        currentUndelivered,
                        currentLate,
                        currentDelay,
                        currentTransit
                    ),
                    formatObjective(
                        bestUndelivered,
                        bestLate,
                        bestDelay,
                        bestTransit
                    )
                );
                System.out.println(
                    "  diag(" + diagnosticsWindow.snapshotAndReset() + ")"
                );
            }
        }

        long elapsed = System.currentTimeMillis() - startTimeMs;
        System.out.println("=== Tabu Search finalizado ===");
        System.out.printf(
            "Iteraciones: %d, Mejoras: %d, Tiempo: %d ms%n",
            iteration - 1,
            improvementCount,
            elapsed
        );
        System.out.println("Mejor solución: " + bestSolution);
        System.out.println(
            "Mejor objetivo: " +
                formatObjective(
                    bestUndelivered,
                    bestLate,
                    bestDelay,
                    bestTransit
                )
        );
        System.out.println(
            "Estado del semáforo: " + getSemaphoreStatus(bestSolution)
        );

        return bestSolution;
    }

    private void printFeasibilityBaseline() {
        long impossibleRequests = 0;
        long impossibleSuitcases = 0;
        long onTimePossibleSuitcases = 0;

        for (ShipmentRequest req : requests) {
            List<List<Flight>> routes = getRoutes(req);
            boolean hasOnTimeIgnoringCapacity = false;
            double deadlineAbs = req.getCreationTime() + req.getDeadline();

            for (List<Flight> path : routes) {
                double arrival = computeArrivalTime(req, path);
                if (arrival <= deadlineAbs + EPS) {
                    hasOnTimeIgnoringCapacity = true;
                    break;
                }
            }

            if (hasOnTimeIgnoringCapacity) {
                onTimePossibleSuitcases += req.getQuantity();
            } else {
                impossibleRequests++;
                impossibleSuitcases += req.getQuantity();
            }
        }

        System.out.printf(
            "Baseline factibilidad (sin capacidad): imposibles a tiempo=%d solicitudes, %d maletas%n",
            impossibleRequests,
            impossibleSuitcases
        );
        System.out.printf(
            "Piso teórico de tardanza por red/horarios: >= %d maletas tardías%n",
            impossibleSuitcases
        );
        System.out.printf(
            "Maletas potencialmente a tiempo (sin capacidad): %d%n",
            onTimePossibleSuitcases
        );
    }

    private Solution generateInitialSolution() {
        Solution solution = new Solution();
        network.resetLoads();

        List<ShipmentRequest> sorted = new ArrayList<>(requests);
        sorted.sort((a, b) -> {
            int cmpDeadline = Double.compare(a.getDeadline(), b.getDeadline());
            if (cmpDeadline != 0) return cmpDeadline;
            return Integer.compare(b.getQuantity(), a.getQuantity());
        });

        for (ShipmentRequest req : sorted) {
            RouteAssignment chosen = findBestGreedyRoute(req);
            if (chosen != null && !chosen.getFlights().isEmpty()) {
                int qty = req.getQuantity();
                for (Flight f : chosen.getFlights()) {
                    f.assign(qty);
                }
                solution.addRoute(chosen);
            } else {
                solution.addRoute(new RouteAssignment(req));
            }
        }
        return solution;
    }

    private RouteAssignment findBestGreedyRoute(ShipmentRequest req) {
        return findBestGreedyRoute(req, null);
    }

    private RouteAssignment findBestGreedyRoute(
        ShipmentRequest req,
        List<Flight> avoidPath
    ) {
        List<List<Flight>> allRoutes = getRoutes(req);
        if (allRoutes.isEmpty()) return null;

        int qty = req.getQuantity();
        List<PathScore> feasible = new ArrayList<>();

        for (List<Flight> path : allRoutes) {
            if (!canAssignPath(path, qty)) continue;
            if (avoidPath != null && samePath(path, avoidPath)) continue;
            feasible.add(scorePath(req, path));
        }

        if (feasible.isEmpty()) {
            return null;
        }

        feasible.sort((a, b) -> {
            int cmp = Double.compare(a.arrival, b.arrival);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.hops, b.hops);
            if (cmp != 0) return cmp;
            return Double.compare(a.transit, b.transit);
        });

        int top = Math.min(INITIAL_TOP_K, feasible.size());
        PathScore selected = feasible.get(random.nextInt(top));
        return new RouteAssignment(req, selected.path);
    }

    private void precomputeRoutes(List<ShipmentRequest> reqs) {
        for (ShipmentRequest req : reqs) {
            List<List<Flight>> routes = network.findRoutes(
                req.getOrigin(),
                req.getDestination(),
                req.getCreationTime(),
                config.getMaxHops(),
                MAX_RUTAS_POR_CONSULTA
            );
            candidateRoutes.put(req, routes);
        }
    }

    private List<List<Flight>> getRoutes(ShipmentRequest req) {
        List<List<Flight>> routes = candidateRoutes.get(req);
        return routes != null ? routes : Collections.emptyList();
    }

    private MoveCandidateResult exploreNeighborhood(IterationStats stats) {
        int n = currentSolution.size();
        if (n == 0) return null;

        int candidateCount = computeCandidateCount(n);
        NeighborhoodSample sample = selectNeighborhoodIndices(candidateCount);
        stats.sampledIndices = sample.indices.size();
        stats.sampledCriticalIndices = sample.criticalCount;

        MoveCandidateResult bestCandidate = null;

        for (int idx : sample.indices) {
            if (isTimeLimitReached()) break;

            RouteAssignment currentRoute = currentSolution.getRoute(idx);
            ShipmentRequest req = currentRoute.getRequest();
            int qty = req.getQuantity();

            RouteContribution oldC = evaluateCurrentRoute(currentRoute);
            List<List<Flight>> alternatives = getRoutes(req);
            if (alternatives.isEmpty()) continue;

            for (List<Flight> altPath : alternatives) {
                if (samePath(currentRoute.getFlights(), altPath)) continue;

                stats.candidatesEvaluated++;

                for (Flight f : currentRoute.getFlights()) f.unassign(qty);
                boolean capOk = canAssignPath(altPath, qty);
                for (Flight f : currentRoute.getFlights()) f.assign(qty);

                if (!capOk) {
                    stats.capacityRejected++;
                    continue;
                }

                double arrival = computeArrivalTime(req, altPath);
                RouteContribution newC = evaluateProjectedRoute(
                    req,
                    altPath,
                    true,
                    arrival
                );

                long candUndel =
                    currentUndelivered - oldC.undelivered + newC.undelivered;
                long candLate = currentLate - oldC.late + newC.late;
                double candDelay = currentDelay - oldC.delay + newC.delay;
                double candTransit =
                    currentTransit - oldC.transit + newC.transit;

                TabuMove move = new TabuMove(idx, hashRoute(altPath));
                boolean isTabu = isTabu(move);

                if (
                    isTabu &&
                    compareScores(
                        candUndel,
                        candLate,
                        candDelay,
                        candTransit,
                        bestUndelivered,
                        bestLate,
                        bestDelay,
                        bestTransit
                    ) >=
                    0
                ) {
                    stats.tabuBlocked++;
                    continue;
                }

                stats.feasibleCandidates++;
                int cmpCurrent = compareScores(
                    candUndel,
                    candLate,
                    candDelay,
                    candTransit,
                    currentUndelivered,
                    currentLate,
                    currentDelay,
                    currentTransit
                );
                if (cmpCurrent < 0) stats.improvingCandidates++;
                else if (cmpCurrent == 0) stats.equalCandidates++;

                if (
                    bestCandidate == null ||
                    compareScores(
                        candUndel,
                        candLate,
                        candDelay,
                        candTransit,
                        bestCandidate.resultUndelivered,
                        bestCandidate.resultLate,
                        bestCandidate.resultDelay,
                        bestCandidate.resultTransit
                    ) <
                    0
                ) {
                    bestCandidate = new MoveCandidateResult(
                        move,
                        idx,
                        new RouteAssignment(req, altPath),
                        candUndel,
                        candLate,
                        candDelay,
                        candTransit,
                        "REROUTE"
                    );
                }
            }
        }

        MoveCandidateResult kickCandidate = exploreBottleneckKick(
            stats,
            bestCandidate
        );
        if (kickCandidate != null) {
            if (
                bestCandidate == null ||
                compareScores(
                    kickCandidate.resultUndelivered,
                    kickCandidate.resultLate,
                    kickCandidate.resultDelay,
                    kickCandidate.resultTransit,
                    bestCandidate.resultUndelivered,
                    bestCandidate.resultLate,
                    bestCandidate.resultDelay,
                    bestCandidate.resultTransit
                ) <
                0
            ) {
                bestCandidate = kickCandidate;
            }
        }

        return bestCandidate;
    }

    private MoveCandidateResult exploreBottleneckKick(
        IterationStats stats,
        MoveCandidateResult incumbent
    ) {
        if (lateOrUndeliveredIndices.isEmpty() || flightUsers.isEmpty()) {
            return null;
        }

        MoveCandidateResult best = incumbent;

        List<Integer> targets = new ArrayList<>(lateOrUndeliveredIndices);
        Collections.shuffle(targets, random);
        int targetLimit = Math.min(MAX_BOTTLENECK_TARGETS, targets.size());

        for (int t = 0; t < targetLimit; t++) {
            if (isTimeLimitReached()) break;

            int lateIdx = targets.get(t);
            RouteAssignment lateRoute = currentSolution.getRoute(lateIdx);
            Flight bottleneck = pickBottleneckFlight(lateRoute);
            if (bottleneck == null) continue;

            List<Integer> users = flightUsers.getOrDefault(
                bottleneck,
                Collections.emptyList()
            );
            if (users.size() <= 1) continue;

            List<Integer> donors = new ArrayList<>(users);
            Collections.shuffle(donors, random);

            int donorTrials = 0;
            for (int donorIdx : donors) {
                if (donorIdx == lateIdx) continue;
                if (donorTrials >= MAX_DONOR_TRIALS) break;
                donorTrials++;

                RouteAssignment donorRoute = currentSolution.getRoute(donorIdx);
                if (
                    !donorRoute.isFeasible() || !donorRoute.isOnTime()
                ) continue;

                ShipmentRequest donorReq = donorRoute.getRequest();
                int qty = donorReq.getQuantity();
                RouteContribution oldC = evaluateCurrentRoute(donorRoute);

                List<List<Flight>> donorAlternatives = getRoutes(donorReq);
                int altTrials = 0;

                for (List<Flight> altPath : donorAlternatives) {
                    if (altTrials >= MAX_ALT_TRIALS_PER_DONOR) break;
                    altTrials++;

                    if (samePath(donorRoute.getFlights(), altPath)) continue;
                    if (altPath.contains(bottleneck)) continue;

                    stats.candidatesEvaluated++;

                    for (Flight f : donorRoute.getFlights()) f.unassign(qty);
                    boolean capOk = canAssignPath(altPath, qty);
                    for (Flight f : donorRoute.getFlights()) f.assign(qty);

                    if (!capOk) {
                        stats.capacityRejected++;
                        continue;
                    }

                    double arrival = computeArrivalTime(donorReq, altPath);
                    RouteContribution newC = evaluateProjectedRoute(
                        donorReq,
                        altPath,
                        true,
                        arrival
                    );

                    long candUndel =
                        currentUndelivered -
                        oldC.undelivered +
                        newC.undelivered;
                    long candLate = currentLate - oldC.late + newC.late;
                    double candDelay = currentDelay - oldC.delay + newC.delay;
                    double candTransit =
                        currentTransit - oldC.transit + newC.transit;

                    TabuMove move = new TabuMove(donorIdx, hashRoute(altPath));
                    boolean isTabu = isTabu(move);
                    if (
                        isTabu &&
                        compareScores(
                            candUndel,
                            candLate,
                            candDelay,
                            candTransit,
                            bestUndelivered,
                            bestLate,
                            bestDelay,
                            bestTransit
                        ) >=
                        0
                    ) {
                        stats.tabuBlocked++;
                        continue;
                    }

                    boolean worsensPrimary =
                        candUndel > currentUndelivered ||
                        candLate > currentLate;
                    boolean improvesSecondaryOnly =
                        candUndel == currentUndelivered &&
                        candLate == currentLate &&
                        candDelay < currentDelay - EPS;
                    boolean improvesPrimary =
                        candUndel < currentUndelivered ||
                        candLate < currentLate;
                    if (
                        worsensPrimary ||
                        (!improvesPrimary && !improvesSecondaryOnly)
                    ) {
                        stats.rejectedKick++;
                        continue;
                    }

                    stats.feasibleCandidates++;

                    if (
                        best == null ||
                        compareScores(
                            candUndel,
                            candLate,
                            candDelay,
                            candTransit,
                            best.resultUndelivered,
                            best.resultLate,
                            best.resultDelay,
                            best.resultTransit
                        ) <
                        0
                    ) {
                        best = new MoveCandidateResult(
                            move,
                            donorIdx,
                            new RouteAssignment(donorReq, altPath),
                            candUndel,
                            candLate,
                            candDelay,
                            candTransit,
                            "BOTTLENECK_KICK"
                        );
                    }
                }
            }
        }

        return best;
    }

    private void applyMove(MoveCandidateResult candidate) {
        applySingleReroute(candidate.requestIndex, candidate.newRoute);
        currentUndelivered = candidate.resultUndelivered;
        currentLate = candidate.resultLate;
        currentDelay = candidate.resultDelay;
        currentTransit = candidate.resultTransit;
    }

    private void applySingleReroute(int index, RouteAssignment newRoute) {
        RouteAssignment old = currentSolution.getRoute(index);
        int qty = old.getRequest().getQuantity();

        for (Flight f : old.getFlights()) {
            f.unassign(qty);
        }
        for (Flight f : newRoute.getFlights()) {
            f.assign(qty);
        }

        currentSolution.setRoute(index, newRoute);
    }

    private void addToTabuList(MoveCandidateResult candidate) {
        tabuList.put(candidate.move, iteration + currentTabuTenure);
    }

    private void purgeTabuList() {
        tabuList.entrySet().removeIf(entry -> entry.getValue() <= iteration);
    }

    private void diversify() {
        int perturbCount = Math.max(1, currentSolution.size() / 5);
        diversifyDirected(perturbCount, 0.70);
    }

    private void intensify() {
        currentSolution = bestSolution.copy();
        reassignAllLoads();
        recalculateCurrentScoreFromSolution();
    }

    private void recoverFromStagnation() {
        int since = iteration - lastImprovementIteration;
        System.out.printf(
            "Iter %d: estancamiento (%d sin mejora). Activando recuperación.%n",
            iteration,
            since
        );

        currentTabuTenure = Math.min(
            Math.max(1, config.getTabuTenure() * 3),
            config.getTabuTenure() + 40
        );
        tabuList.clear();

        currentSolution = bestSolution.copy();
        reassignAllLoads();
        recalculateCurrentScoreFromSolution();
        refreshCriticalSets();

        int perturbCount = Math.max(1, currentSolution.size() / 8);
        diversifyDirected(perturbCount, 0.85);
    }

    private void diversifyDirected(int perturbCount, double criticalBias) {
        int size = currentSolution.size();
        if (size == 0) return;

        Set<Integer> perturbed = selectPerturbationIndices(
            perturbCount,
            criticalBias
        );
        List<RouteAssignment> rebuilt = new ArrayList<>(size);

        network.resetLoads();

        for (int i = 0; i < size; i++) {
            RouteAssignment current = currentSolution.getRoute(i);
            ShipmentRequest req = current.getRequest();
            int qty = req.getQuantity();

            RouteAssignment chosen;
            if (perturbed.contains(i)) {
                chosen = findNearBestFeasibleRoutePreferDifferent(
                    req,
                    current.getFlights()
                );
                if (chosen == null) {
                    chosen = findBestGreedyRoute(req);
                }
            } else {
                if (
                    !current.getFlights().isEmpty() &&
                    canAssignPath(current.getFlights(), qty)
                ) {
                    chosen = current.copy();
                } else {
                    chosen = findBestGreedyRoute(req);
                }
            }

            if (
                chosen == null ||
                chosen.getFlights().isEmpty() ||
                !canAssignPath(chosen.getFlights(), qty)
            ) {
                rebuilt.add(new RouteAssignment(req));
                continue;
            }

            for (Flight f : chosen.getFlights()) {
                f.assign(qty);
            }
            rebuilt.add(chosen);
        }

        currentSolution = new Solution(rebuilt);
        recalculateCurrentScoreFromSolution();
        refreshCriticalSets();
    }

    private RouteAssignment findNearBestFeasibleRoutePreferDifferent(
        ShipmentRequest req,
        List<Flight> currentPath
    ) {
        List<List<Flight>> routes = getRoutes(req);
        if (routes.isEmpty()) return null;

        int qty = req.getQuantity();
        List<PathScore> feasible = new ArrayList<>();
        for (List<Flight> path : routes) {
            if (samePath(path, currentPath) && routes.size() > 1) continue;
            if (canAssignPath(path, qty)) {
                feasible.add(scorePath(req, path));
            }
        }

        if (feasible.isEmpty()) {
            return null;
        }

        feasible.sort((a, b) -> {
            int cmp = Double.compare(a.arrival, b.arrival);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(a.hops, b.hops);
            if (cmp != 0) return cmp;
            return Double.compare(a.transit, b.transit);
        });

        int top = Math.min(INITIAL_TOP_K + 1, feasible.size());
        PathScore selected = feasible.get(random.nextInt(top));
        return new RouteAssignment(req, selected.path);
    }

    private Set<Integer> selectPerturbationIndices(
        int perturbCount,
        double criticalBias
    ) {
        int n = currentSolution.size();
        int max = Math.min(Math.max(1, perturbCount), n);
        double bias = Math.max(0.0, Math.min(1.0, criticalBias));
        int criticalTarget = (int) Math.round(max * bias);

        LinkedHashSet<Integer> selected = new LinkedHashSet<>(max);

        if (!criticalIndices.isEmpty() && criticalTarget > 0) {
            List<Integer> shuffledCritical = new ArrayList<>(criticalIndices);
            Collections.shuffle(shuffledCritical, random);
            for (int idx : shuffledCritical) {
                if (selected.size() >= criticalTarget) break;
                selected.add(idx);
            }
        }

        while (selected.size() < max) {
            selected.add(random.nextInt(n));
        }

        return selected;
    }

    private void refreshCriticalSets() {
        criticalIndices.clear();
        lateOrUndeliveredIndices.clear();
        flightUsers.clear();

        int n = currentSolution.size();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            RouteAssignment ra = currentSolution.getRoute(i);
            for (Flight f : ra.getFlights()) {
                flightUsers.computeIfAbsent(f, k -> new ArrayList<>()).add(i);
            }
        }

        Set<Flight> bottleneckFlights = new HashSet<>();
        double bottleneckThreshold = Math.max(
            0.0,
            Math.min(1.0, config.getBottleneckUtilization())
        );
        for (Flight f : flightUsers.keySet()) {
            if (f.getCapacity() <= 0) continue;
            double util = (double) f.getAssignedLoad() / f.getCapacity();
            if (util >= bottleneckThreshold) {
                bottleneckFlights.add(f);
            }
        }

        double slackThreshold = Math.max(0.0, config.getCriticalSlackDays());
        for (int i = 0; i < n; i++) {
            RouteAssignment ra = currentSolution.getRoute(i);
            boolean undelivered = !ra.isFeasible() || ra.getFlights().isEmpty();
            if (undelivered) {
                criticalIndices.add(i);
                lateOrUndeliveredIndices.add(i);
                continue;
            }

            if (!ra.isOnTime()) {
                criticalIndices.add(i);
                lateOrUndeliveredIndices.add(i);
                continue;
            }

            double absoluteDeadline =
                ra.getRequest().getCreationTime() +
                ra.getRequest().getDeadline();
            double slack = absoluteDeadline - ra.getArrivalTime();
            if (slack <= slackThreshold + EPS) {
                criticalIndices.add(i);
                continue;
            }

            for (Flight f : ra.getFlights()) {
                if (
                    bottleneckFlights.contains(f) &&
                    slack <= (2.0 * slackThreshold + EPS)
                ) {
                    criticalIndices.add(i);
                    break;
                }
            }
        }

        if (criticalIndices.isEmpty()) {
            int fallback = Math.max(1, n / 20);
            Set<Integer> randomFallback = selectPerturbationIndices(
                fallback,
                0.0
            );
            criticalIndices.addAll(randomFallback);
        }
    }

    private boolean shouldRefreshCriticalSets() {
        if (iteration <= 1) return true;
        int refresh = Math.max(1, config.getCriticalRefreshInterval());
        return criticalIndices.isEmpty() || iteration % refresh == 0;
    }

    private int computeCandidateCount(int solutionSize) {
        int candidateCount;
        if (config.getNeighborhoodRatio() > 0) {
            candidateCount = (int) Math.ceil(
                solutionSize * config.getNeighborhoodRatio()
            );
        } else {
            candidateCount = config.getNeighborhoodSize();
        }
        candidateCount = Math.max(1, candidateCount);
        return Math.min(candidateCount, solutionSize);
    }

    private NeighborhoodSample selectNeighborhoodIndices(int candidateCount) {
        int n = currentSolution.size();
        if (n == 0) return new NeighborhoodSample(Collections.emptyList(), 0);

        int total = Math.min(Math.max(1, candidateCount), n);
        double share = Math.max(
            0.0,
            Math.min(1.0, config.getCriticalNeighborhoodShare())
        );
        int criticalTarget = (int) Math.round(total * share);

        LinkedHashSet<Integer> selected = new LinkedHashSet<>(total);
        int criticalSelected = 0;

        if (!criticalIndices.isEmpty() && criticalTarget > 0) {
            List<Integer> shuffledCritical = new ArrayList<>(criticalIndices);
            Collections.shuffle(shuffledCritical, random);
            for (int idx : shuffledCritical) {
                if (selected.size() >= criticalTarget) break;
                if (selected.add(idx)) {
                    criticalSelected++;
                }
            }
        }

        while (selected.size() < total) {
            selected.add(random.nextInt(n));
        }

        return new NeighborhoodSample(
            new ArrayList<>(selected),
            criticalSelected
        );
    }

    private boolean canAssignPath(List<Flight> path, int qty) {
        for (Flight f : path) {
            if (!f.canAssign(qty)) return false;
        }
        return true;
    }

    private boolean samePath(List<Flight> a, List<Flight> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    private int hashRoute(List<Flight> path) {
        int h = 1;
        for (Flight f : path) {
            h = 31 * h + f.getId().hashCode();
        }
        return h;
    }

    private boolean isTabu(TabuMove move) {
        Integer expiry = tabuList.get(move);
        return expiry != null && expiry > iteration;
    }

    private Flight pickBottleneckFlight(RouteAssignment route) {
        Flight best = null;
        double bestUtil = -1.0;
        double threshold = Math.max(
            0.0,
            Math.min(1.0, config.getBottleneckUtilization())
        );

        for (Flight f : route.getFlights()) {
            if (f.getCapacity() <= 0) continue;
            double util = (double) f.getAssignedLoad() / f.getCapacity();
            if (util >= threshold && util > bestUtil) {
                bestUtil = util;
                best = f;
            }
        }
        return best;
    }

    private double computeArrivalTime(ShipmentRequest req, List<Flight> path) {
        if (path.isEmpty()) return 999.0;

        double currentTime = req.getCreationTime();
        for (Flight f : path) {
            double wait = Math.max(0.0, f.getDepartureTime() - currentTime);
            currentTime += wait + f.getTransitTime();
        }
        return currentTime;
    }

    private double computeHopPenalty(List<Flight> path, int qty) {
        return path.size() * HOP_WEIGHT * qty;
    }

    private PathScore scorePath(ShipmentRequest req, List<Flight> path) {
        double arrival = computeArrivalTime(req, path);
        double transit = Math.max(0.0, arrival - req.getCreationTime()) * req.getQuantity();
        return new PathScore(path, arrival, path.size(), transit);
    }

    private RouteContribution evaluateCurrentRoute(RouteAssignment ra) {
        int qty = ra.getRequest().getQuantity();
        boolean feasible = !ra.getFlights().isEmpty() && ra.isFeasible();
        if (!feasible) {
            return new RouteContribution(qty, 0, 0.0, 0.0);
        }

        ShipmentRequest req = ra.getRequest();
        double arrival = ra.getArrivalTime();
        double absDeadline = req.getCreationTime() + req.getDeadline();
        boolean late = arrival > absDeadline + EPS;
        double delay = late ? (arrival - absDeadline) * qty : 0.0;
        double transit =
            (Math.max(0.0, arrival - req.getCreationTime()) * qty) +
            computeHopPenalty(ra.getFlights(), qty);
        return new RouteContribution(0, late ? qty : 0, delay, transit);
    }

    private RouteContribution evaluateProjectedRoute(
        ShipmentRequest req,
        List<Flight> path,
        boolean feasible,
        double arrival
    ) {
        int qty = req.getQuantity();
        if (!feasible || path.isEmpty()) {
            return new RouteContribution(qty, 0, 0.0, 0.0);
        }

        double absDeadline = req.getCreationTime() + req.getDeadline();
        boolean late = arrival > absDeadline + EPS;
        double delay = late ? (arrival - absDeadline) * qty : 0.0;
        double transit =
            (Math.max(0.0, arrival - req.getCreationTime()) * qty) +
            computeHopPenalty(path, qty);
        return new RouteContribution(0, late ? qty : 0, delay, transit);
    }

    private void recalculateCurrentScoreFromSolution() {
        long undel = 0;
        long late = 0;
        double delay = 0.0;
        double transit = 0.0;

        for (RouteAssignment ra : currentSolution.getRoutes()) {
            RouteContribution c = evaluateCurrentRoute(ra);
            undel += c.undelivered;
            late += c.late;
            delay += c.delay;
            transit += c.transit;
        }

        currentUndelivered = undel;
        currentLate = late;
        currentDelay = delay;
        currentTransit = transit;
    }

    private void copyCurrentScoreToBest() {
        bestUndelivered = currentUndelivered;
        bestLate = currentLate;
        bestDelay = currentDelay;
        bestTransit = currentTransit;
    }

    private boolean isCurrentBetterThanBest() {
        return (
            compareScores(
                currentUndelivered,
                currentLate,
                currentDelay,
                currentTransit,
                bestUndelivered,
                bestLate,
                bestDelay,
                bestTransit
            ) <
            0
        );
    }

    private int compareScores(
        long undelA,
        long lateA,
        double delayA,
        double transitA,
        long undelB,
        long lateB,
        double delayB,
        double transitB
    ) {
        if (undelA != undelB) return Long.compare(undelA, undelB);
        if (lateA != lateB) return Long.compare(lateA, lateB);

        int delayCmp = Double.compare(delayA, delayB);
        if (Math.abs(delayA - delayB) > EPS && delayCmp != 0) return delayCmp;

        if (Math.abs(transitA - transitB) <= EPS) return 0;
        return Double.compare(transitA, transitB);
    }

    private String formatObjective(
        long undelivered,
        long late,
        double delay,
        double transit
    ) {
        return String.format(
            "(sinRuta=%d, tarde=%d, delay=%.2f, tránsito=%.2f)",
            undelivered,
            late,
            delay,
            transit
        );
    }

    private void reassignAllLoads() {
        network.resetLoads();
        for (RouteAssignment ra : currentSolution.getRoutes()) {
            int qty = ra.getRequest().getQuantity();
            for (Flight f : ra.getFlights()) {
                f.assign(qty);
            }
        }
    }

    public Solution replanify(Solution currentSol, String cancelledFlightId) {
        System.out.println(
            "Replanificación disparada por cancelación de: " + cancelledFlightId
        );
        network.cancelFlight(cancelledFlightId);

        List<Integer> affected = new ArrayList<>();
        for (int i = 0; i < currentSol.size(); i++) {
            for (Flight f : currentSol.getRoute(i).getFlights()) {
                if (f.getId().equals(cancelledFlightId)) {
                    affected.add(i);
                    break;
                }
            }
        }
        System.out.println("Envíos afectados: " + affected.size());

        List<ShipmentRequest> affectedReqs = new ArrayList<>(affected.size());
        for (int idx : affected) {
            affectedReqs.add(currentSol.getRoute(idx).getRequest());
        }
        precomputeRoutes(affectedReqs);

        this.currentSolution = currentSol;
        reassignAllLoads();
        recalculateCurrentScoreFromSolution();

        for (int idx : affected) {
            ShipmentRequest req = currentSolution.getRoute(idx).getRequest();
            RouteAssignment newRoute = findBestGreedyRoute(req);
            if (newRoute != null) {
                applySingleReroute(idx, newRoute);
            } else {
                applySingleReroute(idx, new RouteAssignment(req));
            }
        }

        recalculateCurrentScoreFromSolution();
        bestSolution = currentSolution.copy();
        copyCurrentScoreToBest();
        bestFitness = bestSolution.getFitness();
        tabuList.clear();
        currentTabuTenure = Math.max(1, config.getTabuTenure());

        int replanIter = Math.min(100, config.getMaxIterations());
        startTimeMs = System.currentTimeMillis();

        refreshCriticalSets();
        for (iteration = 1; iteration <= replanIter; iteration++) {
            if (isTimeLimitReached()) break;
            if (shouldRefreshCriticalSets()) refreshCriticalSets();

            IterationStats stats = new IterationStats();
            MoveCandidateResult best = exploreNeighborhood(stats);
            if (best != null) {
                applyMove(best);
                addToTabuList(best);

                if (isCurrentBetterThanBest()) {
                    bestSolution = currentSolution.copy();
                    copyCurrentScoreToBest();
                    bestFitness = bestSolution.getFitness();
                }
            }
            purgeTabuList();
        }

        System.out.println(
            "Replanificación completada. Nueva solución: " + bestSolution
        );
        return bestSolution;
    }

    public String getSemaphoreStatus(Solution solution) {
        if (solution.getUndeliveredCount() > 0) return "RED";
        if (solution.getLateCount() > 0) return "AMBER";
        return "GREEN";
    }

    public List<Double> getFitnessHistory() {
        return Collections.unmodifiableList(fitnessHistory);
    }

    public int getImprovementCount() {
        return improvementCount;
    }

    public int getIterationsRun() {
        return iteration - 1;
    }

    private boolean isTimeLimitReached() {
        return (
            (System.currentTimeMillis() - startTimeMs) >=
            config.getTimeLimitMs()
        );
    }

    public void printReport() {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;

        System.out.println("\n========== REPORTE TABU SEARCH ==========");
        System.out.println("Iteraciones ejecutadas: " + getIterationsRun());
        System.out.println("Mejoras registradas:    " + improvementCount);
        System.out.println("Total solicitudes:      " + sol.getTotalRequests());
        System.out.println(
            "Total maletas:          " + sol.getTotalSuitcases()
        );
        System.out.println(
            "Maletas entregadas:     " + sol.getDeliveredCount()
        );
        System.out.println(
            "Maletas no entregadas:  " + sol.getUndeliveredCount()
        );
        System.out.println("Maletas tardías:        " + sol.getLateCount());
        System.out.printf(
            "Retraso total:          %.2f días%n",
            sol.getTotalDelay()
        );
        System.out.println(
            "Overflow capacidad:     " + sol.getCapacityOverflow()
        );
        System.out.println(
            "Overflow almacén:       " + sol.getWarehouseOverflow()
        );
        System.out.printf("Fitness:                %.2f%n", sol.getFitness());
        System.out.println(
            "Objetivo lexicográfico: " +
                formatObjective(
                    bestUndelivered,
                    bestLate,
                    bestDelay,
                    bestTransit
                )
        );
        System.out.println(
            "Semáforo:               " + getSemaphoreStatus(sol)
        );
        System.out.printf("Tiempo de ejecución:    %d ms%n", elapsed);
        System.out.println("=========================================\n");
    }

    public void writeReportMachine(PrintWriter pw, String label) {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;

        pw.println(
            "## BLOQUE=" +
                label +
                " TS=" +
                LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                ) +
                " ITER=" +
                getIterationsRun() +
                " FITNESS=" +
                String.format("%.2f", sol.getFitness()) +
                " OBJ=" +
                formatObjective(
                    bestUndelivered,
                    bestLate,
                    bestDelay,
                    bestTransit
                ) +
                " SEMAFORO=" +
                getSemaphoreStatus(sol) +
                " MS=" +
                elapsed
        );
        pw.println(
            "REQ_ID|ORIGIN|DEST|QTY|CREATION_TIME|DEADLINE|FLIGHTS|ARRIVAL_TIME|STATUS|DELAY_DAYS"
        );

        for (RouteAssignment ra : sol.getRoutes()) {
            ShipmentRequest req = ra.getRequest();

            String flights;
            if (ra.getFlights().isEmpty()) {
                flights = "NONE";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ra.getFlights().size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(ra.getFlights().get(i).getId());
                }
                flights = sb.toString();
            }

            String status;
            if (!ra.isFeasible()) status = "UNDELIVERED";
            else if (ra.isOnTime()) status = "ON_TIME";
            else status = "LATE";

            pw.printf(
                "%s|%s|%s|%d|%.6f|%.6f|%s|%.6f|%s|%.4f%n",
                req.getId(),
                req.getOrigin().getCode(),
                req.getDestination().getCode(),
                req.getQuantity(),
                req.getCreationTime(),
                req.getDeadline(),
                flights,
                ra.getArrivalTime(),
                status,
                ra.getDelay()
            );
        }

        pw.println(
            "## RESUMEN" +
                " SOLICITUDES=" +
                sol.getTotalRequests() +
                " MALETAS=" +
                sol.getTotalSuitcases() +
                " ENTREGADAS=" +
                sol.getDeliveredCount() +
                " NO_ENTREGADAS=" +
                sol.getUndeliveredCount() +
                " TARDIAS=" +
                sol.getLateCount() +
                String.format(" RETRASO_TOTAL=%.2f", sol.getTotalDelay()) +
                " OVERFLOW_CAP=" +
                sol.getCapacityOverflow() +
                String.format(" FITNESS=%.2f", sol.getFitness()) +
                " OBJ=" +
                formatObjective(
                    bestUndelivered,
                    bestLate,
                    bestDelay,
                    bestTransit
                ) +
                " SEMAFORO=" +
                getSemaphoreStatus(sol)
        );
        pw.println();
        pw.flush();
    }

    public void writeReportHuman(PrintWriter pw, String label) {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;

        pw.println(
            "══════════════════════════════════════════════════════════════"
        );
        pw.println(
            "  " +
                label +
                " — Tabu Search · " +
                LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")
                )
        );
        pw.println(
            "══════════════════════════════════════════════════════════════"
        );
        pw.printf(
            "  Iteraciones: %d  |  Mejoras: %d  |  Tiempo: %d ms%n",
            getIterationsRun(),
            improvementCount,
            elapsed
        );
        pw.printf(
            "  Solicitudes: %d  |  Maletas: %d  |  Fitness: %.2f  |  Semáforo: %s%n",
            sol.getTotalRequests(),
            sol.getTotalSuitcases(),
            sol.getFitness(),
            getSemaphoreStatus(sol)
        );
        pw.printf(
            "  Objetivo: %s%n",
            formatObjective(bestUndelivered, bestLate, bestDelay, bestTransit)
        );
        pw.printf(
            "  Entregadas: %d  |  No entregadas: %d  |  Tardías: %d  |  Retraso total: %.2f días%n",
            sol.getDeliveredCount(),
            sol.getUndeliveredCount(),
            sol.getLateCount(),
            sol.getTotalDelay()
        );
        pw.println(
            "──────────────────────────────────────────────────────────────"
        );

        for (RouteAssignment ra : sol.getRoutes()) {
            ShipmentRequest req = ra.getRequest();
            String tag;
            if (!ra.isFeasible()) tag = "[NO_ASIG]";
            else if (!ra.isOnTime()) tag = "[TARDE  ]";
            else tag = "[OK     ]";

            String flightDesc;
            if (ra.getFlights().isEmpty()) {
                flightDesc = "sin vuelo asignado";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ra.getFlights().size(); i++) {
                    Flight f = ra.getFlights().get(i);
                    if (i > 0) sb.append(" → ");
                    sb
                        .append(f.getId())
                        .append("(")
                        .append(f.getOrigin().getCode())
                        .append("→")
                        .append(f.getDestination().getCode())
                        .append(" dep=")
                        .append(String.format("%.3f", f.getDepartureTime()))
                        .append(")");
                }
                flightDesc = sb.toString();
            }

            pw.printf(
                "%s %s | %s → %s | %d maleta(s) | %s%n",
                tag,
                req.getId(),
                req.getOrigin().getCode(),
                req.getDestination().getCode(),
                req.getQuantity(),
                flightDesc
            );

            if (!ra.isFeasible()) {
                pw.printf("           Sin ruta factible asignada%n");
            } else {
                pw.printf(
                    "           Llega: %.4f | Plazo: %.4f",
                    ra.getArrivalTime(),
                    req.getCreationTime() + req.getDeadline()
                );
                if (!ra.isOnTime()) {
                    pw.printf(
                        " | Retraso: %.2f días (%.1f h)",
                        ra.getDelay(),
                        ra.getDelay() * 24
                    );
                }
                pw.println();
            }
        }

        pw.println(
            "══════════════════════════════════════════════════════════════"
        );
        pw.println();
        pw.flush();
    }

    public String getCsvRow(long seed) {
        Solution sol = bestSolution;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return String.format(
            "%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%.2f,%s,%d,%d,%d",
            seed,
            sol.getTotalRequests(),
            sol.getTotalSuitcases(),
            sol.getDeliveredCount(),
            sol.getUndeliveredCount(),
            sol.getLateCount(),
            sol.getTotalDelay(),
            sol.getCapacityOverflow(),
            sol.getWarehouseOverflow(),
            sol.getFitness(),
            getSemaphoreStatus(sol),
            getIterationsRun(),
            improvementCount,
            elapsed
        );
    }

    private static class DelayedPath {

        final List<Flight> path;
        final double delayDays;

        DelayedPath(List<Flight> path, double delayDays) {
            this.path = path;
            this.delayDays = delayDays;
        }
    }

    private static class PathScore {

        final List<Flight> path;
        final double arrival;
        final int hops;
        final double transit;

        PathScore(List<Flight> path, double arrival, int hops, double transit) {
            this.path = path;
            this.arrival = arrival;
            this.hops = hops;
            this.transit = transit;
        }
    }

    private static class NeighborhoodSample {

        final List<Integer> indices;
        final int criticalCount;

        NeighborhoodSample(List<Integer> indices, int criticalCount) {
            this.indices = indices;
            this.criticalCount = criticalCount;
        }
    }

    private static class RouteContribution {

        final long undelivered;
        final long late;
        final double delay;
        final double transit;

        RouteContribution(
            long undelivered,
            long late,
            double delay,
            double transit
        ) {
            this.undelivered = undelivered;
            this.late = late;
            this.delay = delay;
            this.transit = transit;
        }
    }

    private static class MoveCandidateResult {

        final TabuMove move;
        final int requestIndex;
        final RouteAssignment newRoute;
        final long resultUndelivered;
        final long resultLate;
        final double resultDelay;
        final double resultTransit;
        final String moveType;

        MoveCandidateResult(
            TabuMove move,
            int requestIndex,
            RouteAssignment newRoute,
            long resultUndelivered,
            long resultLate,
            double resultDelay,
            double resultTransit,
            String moveType
        ) {
            this.move = move;
            this.requestIndex = requestIndex;
            this.newRoute = newRoute;
            this.resultUndelivered = resultUndelivered;
            this.resultLate = resultLate;
            this.resultDelay = resultDelay;
            this.resultTransit = resultTransit;
            this.moveType = moveType;
        }
    }

    private static class IterationStats {

        long candidatesEvaluated;
        long feasibleCandidates;
        long capacityRejected;
        long tabuBlocked;
        long improvingCandidates;
        long equalCandidates;
        long rejectedKick;
        int sampledIndices;
        int sampledCriticalIndices;
        boolean appliedMove;
        boolean rejectedNonImproving;
        boolean diversified;
        boolean intensified;
        boolean stagnationRecovery;
    }

    private static class DiagnosticsWindow {

        long iterCount;
        long candidatesEvaluated;
        long feasibleCandidates;
        long capacityRejected;
        long tabuBlocked;
        long improvingCandidates;
        long equalCandidates;
        long rejectedKick;
        long sampledIndices;
        long sampledCriticalIndices;
        long appliedMoves;
        long rejectedNonImprovingMoves;
        long diversificationEvents;
        long intensificationEvents;
        long stagnationRecoveries;

        void add(IterationStats s) {
            iterCount++;
            candidatesEvaluated += s.candidatesEvaluated;
            feasibleCandidates += s.feasibleCandidates;
            capacityRejected += s.capacityRejected;
            tabuBlocked += s.tabuBlocked;
            improvingCandidates += s.improvingCandidates;
            equalCandidates += s.equalCandidates;
            rejectedKick += s.rejectedKick;
            sampledIndices += s.sampledIndices;
            sampledCriticalIndices += s.sampledCriticalIndices;
            if (s.appliedMove) appliedMoves++;
            if (s.rejectedNonImproving) rejectedNonImprovingMoves++;
            if (s.diversified) diversificationEvents++;
            if (s.intensified) intensificationEvents++;
            if (s.stagnationRecovery) stagnationRecoveries++;
        }

        String snapshotAndReset() {
            if (iterCount == 0) return "sin datos";

            double avgSample = (double) sampledIndices / iterCount;
            double criticalShare =
                sampledIndices > 0
                    ? ((100.0 * sampledCriticalIndices) / sampledIndices)
                    : 0.0;

            String text = String.format(
                "iters=%d, cand=%d, feas=%d, capRej=%d, tabu=%d, mejorables=%d, igual=%d, kickRej=%d, mov=%d, noImpRej=%d, sample=%.1f, crit=%.1f%%, div=%d, int=%d, rec=%d",
                iterCount,
                candidatesEvaluated,
                feasibleCandidates,
                capacityRejected,
                tabuBlocked,
                improvingCandidates,
                equalCandidates,
                rejectedKick,
                appliedMoves,
                rejectedNonImprovingMoves,
                avgSample,
                criticalShare,
                diversificationEvents,
                intensificationEvents,
                stagnationRecoveries
            );

            iterCount = 0;
            candidatesEvaluated = 0;
            feasibleCandidates = 0;
            capacityRejected = 0;
            tabuBlocked = 0;
            improvingCandidates = 0;
            equalCandidates = 0;
            rejectedKick = 0;
            sampledIndices = 0;
            sampledCriticalIndices = 0;
            appliedMoves = 0;
            rejectedNonImprovingMoves = 0;
            diversificationEvents = 0;
            intensificationEvents = 0;
            stagnationRecoveries = 0;

            return text;
        }
    }
}
