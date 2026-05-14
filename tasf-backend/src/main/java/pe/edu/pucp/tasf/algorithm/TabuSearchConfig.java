package pe.edu.pucp.tasf.algorithm;

/**
 * Parámetros de configuración del algoritmo Tabu Search.
 *
 * Todos los parámetros son ajustables para experimentación numérica (requisito
 * no funcional (b) del enunciado: los dos algoritmos deben ser del tipo
 * metaheurístico y evaluados por experimentación).
 *
 * La clase usa el patrón builder encadenable para una configuración fluida:
 *
 *   new TabuSearchConfig()
 *       .maxIterations(1000)
 *       .tabuTenure(20)
 *       .timeLimitMs(30 * 60 * 1000);
 */
public class TabuSearchConfig {

    // ================= Parámetros de la búsqueda =================

    /** Iteraciones máximas del bucle principal. */
    private int maxIterations = 1_000;

    /** Permanencia de un movimiento en la lista tabú (en iteraciones). */
    private int tabuTenure = 15;

    /** Cantidad de candidatos evaluados por iteración en el vecindario.
     *  Se escala dinámicamente en el solver a max(neighborhoodSize, min(500, n/10))
     *  para garantizar cobertura mínima en instancias con miles de envíos. */
    private int neighborhoodSize = 200;

    /**
     * Fracción de envíos evaluados por iteración en el vecindario (0.0–1.0).
     * 1.0 = vecindario completo (garantiza el mejor movimiento local).
     * 0.8 = evalúa el 80% de los envíos elegidos aleatoriamente.
     * Cuando se establece este valor, tiene precedencia sobre {@code neighborhoodSize}.
     * Default: 0.8.
     */
    private double neighborhoodRatio = 0.8;

    /** Máximo número de escalas (vuelos) por ruta construida. */
    private int maxHops = 3;

    /** Cada cuántas iteraciones se dispara la diversificación. */
    private int diversificationInterval = 100;

    /** Cada cuántas iteraciones se dispara la intensificación. */
    private int intensificationInterval = 50;

    /** Umbral del criterio de aspiración (0.0 = aspirar si mejora el mejor global). */
    private double aspirationThreshold = 0.0;

    /** Tiempo máximo de ejecución (por defecto 15 min por día). */
    private long timeLimitMs = 15 * 60 * 1000;

    /** Semilla del generador pseudoaleatorio (para reproducibilidad). */
    private long seed = 42;

    /** Proporción del vecindario tomada desde envíos críticos [0,1]. */
    private double criticalNeighborhoodShare = 0.75;

    /** Cada cuántas iteraciones se recalcula la lista de envíos críticos. */
    private int criticalRefreshInterval = 25;

    /** Holgura máxima (días) para considerar un envío como crítico cercano al vencimiento. */
    private double criticalSlackDays = 0.12;

    /** Utilización mínima de vuelo para marcar cuello de botella [0,1]. */
    private double bottleneckUtilization = 0.90;

    /** Iteraciones sin mejora global para activar recuperación por estancamiento. */
    private int stagnationIterations = 1200;

    /** Habilita diagnóstico de piso teórico (sin capacidad) al inicio de solve(). */
    private boolean feasibilityBaseline = true;

    // ================= Pesos de penalización (soft constraints) =================

    /** Penalización por cada maleta entregada tarde. */
    private double penaltyLate = 1000.0;

    /** Penalización por violación de capacidad (ruta infactible). */
    private double penaltyCapacity = 5000.0;

    /** Penalización por cada día-maleta de retraso acumulado. */
    private double penaltyDelay = 100.0;

    // ================= Umbrales del semáforo =================
    // Mantenidos por compatibilidad con código existente que los lea vía getter.
    // El semáforo en TabuSearchSolver ya no los usa — la lógica es:
    //   undelivered > 0 → RED | late > 0 → AMBER | else → GREEN

    /** @deprecated no usado por getSemaphoreStatus(); conservado para compatibilidad. */
    @Deprecated
    private double greenThreshold = 0.0;

    /** @deprecated no usado por getSemaphoreStatus(); conservado para compatibilidad. */
    @Deprecated
    private double amberThreshold = 0.0;

    // ================= Builder (setters encadenables) =================

    public TabuSearchConfig maxIterations(int val)            { this.maxIterations = val; return this; }
    public TabuSearchConfig tabuTenure(int val)               { this.tabuTenure = val; return this; }
    public TabuSearchConfig neighborhoodSize(int val)         { this.neighborhoodSize = val; return this; }
    public TabuSearchConfig neighborhoodRatio(double val)     { this.neighborhoodRatio = val; return this; }
    public TabuSearchConfig maxHops(int val)                  { this.maxHops = val; return this; }
    public TabuSearchConfig diversificationInterval(int val)  { this.diversificationInterval = val; return this; }
    public TabuSearchConfig intensificationInterval(int val)  { this.intensificationInterval = val; return this; }
    public TabuSearchConfig timeLimitMs(long val)             { this.timeLimitMs = val; return this; }
    public TabuSearchConfig seed(long val)                    { this.seed = val; return this; }
    public TabuSearchConfig criticalNeighborhoodShare(double val) { this.criticalNeighborhoodShare = val; return this; }
    public TabuSearchConfig criticalRefreshInterval(int val)  { this.criticalRefreshInterval = val; return this; }
    public TabuSearchConfig criticalSlackDays(double val)     { this.criticalSlackDays = val; return this; }
    public TabuSearchConfig bottleneckUtilization(double val) { this.bottleneckUtilization = val; return this; }
    public TabuSearchConfig stagnationIterations(int val)     { this.stagnationIterations = val; return this; }
    public TabuSearchConfig feasibilityBaseline(boolean val)  { this.feasibilityBaseline = val; return this; }
    public TabuSearchConfig penaltyLate(double val)           { this.penaltyLate = val; return this; }
    public TabuSearchConfig penaltyCapacity(double val)       { this.penaltyCapacity = val; return this; }
    public TabuSearchConfig greenThreshold(double val)        { this.greenThreshold = val; return this; }
    public TabuSearchConfig amberThreshold(double val)        { this.amberThreshold = val; return this; }

    // ================= Getters =================

    public int getMaxIterations()           { return maxIterations; }
    public int getTabuTenure()              { return tabuTenure; }
    public int getNeighborhoodSize()        { return neighborhoodSize; }
    public double getNeighborhoodRatio()    { return neighborhoodRatio; }
    public int getMaxHops()                 { return maxHops; }
    public int getDiversificationInterval() { return diversificationInterval; }
    public int getIntensificationInterval() { return intensificationInterval; }
    public double getAspirationThreshold()  { return aspirationThreshold; }
    public long getTimeLimitMs()            { return timeLimitMs; }
    public long getSeed()                   { return seed; }
    public double getCriticalNeighborhoodShare() { return criticalNeighborhoodShare; }
    public int getCriticalRefreshInterval() { return criticalRefreshInterval; }
    public double getCriticalSlackDays()    { return criticalSlackDays; }
    public double getBottleneckUtilization(){ return bottleneckUtilization; }
    public int getStagnationIterations()    { return stagnationIterations; }
    public boolean isFeasibilityBaseline()  { return feasibilityBaseline; }
    public double getPenaltyLate()          { return penaltyLate; }
    public double getPenaltyCapacity()      { return penaltyCapacity; }
    public double getPenaltyDelay()         { return penaltyDelay; }
    public double getGreenThreshold()       { return greenThreshold; }
    public double getAmberThreshold()       { return amberThreshold; }
}
