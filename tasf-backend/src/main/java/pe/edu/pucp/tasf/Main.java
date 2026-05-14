package pe.edu.pucp.tasf;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.tasf.algorithm.TabuSearchConfig;
import pe.edu.pucp.tasf.algorithm.TabuSearchSolver;
import pe.edu.pucp.tasf.io.AirportsLoader;
import pe.edu.pucp.tasf.io.EnviosDataLoader;
import pe.edu.pucp.tasf.model.*;
import pe.edu.pucp.tasf.util.RealNetworkBuilder;

/**
 * Punto de entrada principal del planificador Tabu Search para Tasf.B2B.
 *
 * La red logística siempre se construye a partir de datos reales:
 *   - Aeropuertos : {@code aeropuertos.txt}  (buscado en el directorio actual)
 *   - Vuelos      : {@code planes_vuelo.txt} (buscado en el directorio actual o indicado
 *                   como argumento opcional al final de cada modo)
 *
 * Modos disponibles:
 *
 *   E1  - Simulación de periodo (5 días por defecto).
 *         Lee solicitudes de la carpeta {@code _envios_XXXX_.txt}.
 *         Ejecuta en 30-90 minutos.
 *
 *   E2  - Operación en tiempo real con replanificación (≤5 s/evento).
 *         Lee solicitudes del mismo formato que E1.
 *
 *   E3  - Simulación hasta el colapso.
 *         Parte de los envíos reales y escala la demanda +20%/día.
 *
 *   EXP - Experimentación numérica: N réplicas con seeds 1..N → CSV.
 *
 * Uso:
 *   java pe.edu.pucp.tasf.Main E1  <carpeta_envios> [--start=yyyymmdd] [--days=5] [--flights=path]
 *   java pe.edu.pucp.tasf.Main E2  <carpeta_envios> [--flights=path]
 *   java pe.edu.pucp.tasf.Main E3  <carpeta_envios> [--flights=path]
 *   java pe.edu.pucp.tasf.Main EXP <carpeta_envios> [--start=yyyymmdd] [--days=1] [--replicas=30] [--output=file.csv] [--flights=path]
 */
public class Main {

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // ignorado
        }

        String scenario = args.length > 0 ? args[0].toUpperCase() : "E1";

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     Tasf.B2B - Tabu Search Planner      ║");
        System.out.println("║         Equipo 8H - PUCP 2026-1         ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        switch (scenario) {
            case "E1" -> {
                if (args.length < 2) {
                    printUsageE1();
                    return;
                }
                String folder = args[1];
                Map<String, String> flags = parseFlags(args, 2);
                if (flags == null) return; // error ya impreso

                LocalDate startDate = parseFechaArg(flags.get("start"));
                if (startDate == null && flags.containsKey("start")) return; // fecha inválida

                int numDays = parseIntFlag(flags, "days", 5);
                if (numDays < 0) return; // error ya impreso

                Path flights = requireFlightsFile(flags.get("flights"));
                if (flights == null) return;

                runPeriodSimulation(
                    Paths.get(folder),
                    startDate,
                    numDays,
                    flights
                );
            }
            case "E2" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main E2 <carpeta_envios> [--flights=path]"
                    );
                    return;
                }
                String folder = args[1];
                Map<String, String> flags = parseFlags(args, 2);
                if (flags == null) return;

                Path flights = requireFlightsFile(flags.get("flights"));
                if (flights == null) return;

                runRealTimeSimulation(Paths.get(folder), flights);
            }
            case "E3" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main E3 <carpeta_envios> [--flights=path]"
                    );
                    return;
                }
                String folder = args[1];
                Map<String, String> flags = parseFlags(args, 2);
                if (flags == null) return;

                Path flights = requireFlightsFile(flags.get("flights"));
                if (flights == null) return;

                runCollapseSimulation(Paths.get(folder), flights);
            }
            case "EXP" -> {
                if (args.length < 2) {
                    System.out.println(
                        "Uso: Main EXP <carpeta_envios> [--start=yyyymmdd] [--days=1] [--replicas=30] [--output=file.csv] [--flights=path]"
                    );
                    return;
                }
                String folder = args[1];
                Map<String, String> flags = parseFlags(args, 2);
                if (flags == null) return;

                LocalDate startDate = parseFechaArg(flags.get("start"));
                if (startDate == null && flags.containsKey("start")) return;

                int numDays = parseIntFlag(flags, "days", 1);
                if (numDays < 0) return;

                int replicas = parseIntFlag(flags, "replicas", 30);
                if (replicas < 0) return;

                // Si el usuario no pasó --output, generar nombre bajo output/
                String outputCsvRaw = flags.get("output");
                Path outputCsvPath =
                    outputCsvRaw != null
                        ? Paths.get(outputCsvRaw)
                        : buildExpOutputPath(startDate, numDays);

                Path flights = requireFlightsFile(flags.get("flights"));
                if (flights == null) return;

                runExperiment(
                    Paths.get(folder),
                    startDate,
                    numDays,
                    replicas,
                    outputCsvPath,
                    flights
                );
            }
            default -> {
                System.out.println(
                    "Uso: java pe.edu.pucp.tasf.Main <E1|E2|E3|EXP> <carpeta_envios> [flags...]"
                );
                System.out.println(
                    "  E1  --start=yyyymmdd  --days=5      --flights=path"
                );
                System.out.println(
                    "  E2                                   --flights=path"
                );
                System.out.println(
                    "  E3                                   --flights=path"
                );
                System.out.println(
                    "  EXP --start=yyyymmdd --days=1 --replicas=30 --output=f.csv --flights=path"
                );
            }
        }
    }

    // =========================================================================
    //  RESOLUCIÓN DE ARCHIVOS DE DATOS
    // =========================================================================

    /**
     * Resuelve la ruta al archivo de vuelos: usa la indicada, o busca
     * {@code planes_vuelo.txt} en el directorio de trabajo como fallback.
     * Si no se encuentra ninguno, imprime un error y devuelve {@code null}.
     */
    private static Path requireFlightsFile(String explicit) {
        if (explicit != null) {
            Path p = Paths.get(explicit);
            if (Files.isRegularFile(p)) return p;
            System.err.println(
                "ERROR: planes_vuelo.txt indicado no existe: " + explicit
            );
            return null;
        }
        Path cwd = Paths.get("planes_vuelo.txt");
        if (Files.isRegularFile(cwd)) {
            System.out.println(
                "[Main] Usando planes_vuelo.txt del directorio actual: " +
                    cwd.toAbsolutePath()
            );
            return cwd;
        }
        System.err.println(
            "ERROR: No se encontró planes_vuelo.txt. Indíquelo como último argumento."
        );
        return null;
    }

    // =========================================================================
    //  ARCHIVOS DE SALIDA
    // =========================================================================

    /**
     * Construye el nombre del archivo de salida con el formato:
     *   output/{epoch_segundos}_{MODE}_{yyyyMMdd}_{numDays}_{suffix}.{ext}
     *
     * Crea la carpeta {@code output/} si no existe.
     *
     * @param mode      identificador del escenario (E1, E2, E3, EXP)
     * @param startDate fecha de inicio; si es null se usa "nodate"
     * @param numDays   número de días simulados
     * @param suffix    sufijo descriptivo (p.ej. "machine", "human")
     * @param ext       extensión sin punto (txt, csv)
     */
    private static Path buildOutputPath(
        String mode,
        LocalDate startDate,
        int numDays,
        String suffix,
        String ext
    ) {
        Path outputDir = Paths.get("output");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println(
                "[AVISO] No se pudo crear carpeta output/: " + e.getMessage()
            );
        }
        String runTime = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
        );
        String dateStr =
            startDate != null
                ? startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                : "nodate";
        String filename = String.format(
            "%s_%s_%s_%d_%s.%s",
            runTime,
            mode,
            dateStr,
            numDays,
            suffix,
            ext
        );
        return outputDir.resolve(filename);
    }

    /** Overload sin suffix — mantiene compatibilidad con EXP que genera su propio nombre. */
    private static Path buildOutputPath(
        String mode,
        LocalDate startDate,
        int numDays,
        String ext
    ) {
        return buildOutputPath(mode, startDate, numDays, "out", ext);
    }

    /**
     * Nombre automático para EXP:
     * output/EXP_{yy-MM-dd:hh_mm_ss}_{dateChosen}_{numDays}.csv
     */
    private static Path buildExpOutputPath(LocalDate startDate, int numDays) {
        Path outputDir = Paths.get("output");
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            System.err.println(
                "[AVISO] No se pudo crear carpeta output/: " + e.getMessage()
            );
        }

        String runTs = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yy-MM-dd:hh_mm_ss")
        );
        String dateChosen =
            startDate != null
                ? startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                : "nodate";

        String filename = String.format(
            "EXP_%s_%s_%d.csv",
            runTs,
            dateChosen,
            numDays
        );
        return outputDir.resolve(filename);
    }

    /**
     * Abre un PrintWriter sobre el archivo dado e imprime la cabecera global.
     * Devuelve null si no se pudo abrir (el error ya fue impreso).
     */
    private static PrintWriter openOutputWriter(
        Path path,
        String mode,
        LocalDate startDate,
        int numDays
    ) {
        try {
            PrintWriter pw = new PrintWriter(
                new FileWriter(path.toFile(), false)
            );
            pw.println("# Tasf.B2B — Tabu Search Planner");
            pw.println("# Modo: " + mode);
            pw.println(
                "# Fecha inicio: " +
                    (startDate != null ? startDate : "(inicio de datos)")
            );
            pw.println("# Días simulados: " + numDays);
            pw.println(
                "# Generado: " +
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    )
            );
            pw.println();
            System.out.println("[Salida] Archivo: " + path.toAbsolutePath());
            return pw;
        } catch (IOException e) {
            System.err.println(
                "[ERROR] No se pudo crear archivo de salida: " + e.getMessage()
            );
            return null;
        }
    }

    private static void printUsageE1() {
        System.out.println(
            "Uso: Main E1 <carpeta_envios> [--start=yyyymmdd] [--days=5] [--flights=path]"
        );
        System.out.println(
            "  --start   fecha de inicio en formato yyyymmdd o yyyy-mm-dd (default: primer día en datos)"
        );
        System.out.println("  --days    número de días a simular (default: 5)");
        System.out.println(
            "  --flights ruta al archivo planes_vuelo.txt (default: ./planes_vuelo.txt)"
        );
    }

    /**
     * Parsea los argumentos desde {@code fromIndex} buscando flags con formato
     * {@code --key=value} o {@code --key value} (dos tokens consecutivos).
     *
     * @return mapa de flags encontrados, o {@code null} si hay un flag mal formado
     */
    private static Map<String, String> parseFlags(
        String[] args,
        int fromIndex
    ) {
        Map<String, String> flags = new HashMap<>();
        int i = fromIndex;
        while (i < args.length) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 2) {
                    // --key=value en un solo token
                    flags.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else if (
                    eq == -1 &&
                    i + 1 < args.length &&
                    !args[i + 1].startsWith("--")
                ) {
                    // --key value en dos tokens
                    flags.put(arg.substring(2), args[i + 1]);
                    i++;
                } else {
                    System.err.println(
                        "ERROR: flag mal formada: '" +
                            arg +
                            "'. Use --key=value o --key valor."
                    );
                    return null;
                }
            }
            // args que no empiezan con -- se ignoran (la carpeta ya fue tomada en args[1])
            i++;
        }
        return flags;
    }

    /**
     * Lee una flag entera del mapa; devuelve {@code defaultValue} si no está presente.
     * Imprime error y devuelve {@code -1} si el valor no es un entero válido.
     */
    private static int parseIntFlag(
        Map<String, String> flags,
        String key,
        int defaultValue
    ) {
        String val = flags.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            System.err.println(
                "ERROR: --" +
                    key +
                    " debe ser un entero, recibido: '" +
                    val +
                    "'."
            );
            return -1;
        }
    }

    /**
     * Parsea un argumento de fecha en formato {@code yyyymmdd} o {@code yyyy-mm-dd}.
     * Devuelve {@code null} si el argumento también es {@code null} (el usuario no lo
     * proporcionó, se usará el inicio de los datos).
     * Imprime un error y devuelve {@code null} si el formato es inválido.
     *
     * @param arg cadena recibida desde la línea de comandos, o null si no se indicó
     * @return la fecha parseada, o null
     */
    private static LocalDate parseFechaArg(String arg) {
        if (arg == null) return null; // no se indicó fecha; usar inicio de datos

        // Normalizar: quitar guiones para manejar ambos formatos (yyyymmdd y yyyy-mm-dd)
        String normalizado = arg.replace("-", "");
        try {
            return LocalDate.parse(
                normalizado,
                DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (DateTimeParseException e) {
            System.err.println(
                "ERROR: Fecha de inicio inválida: '" +
                    arg +
                    "'. Use el formato yyyymmdd o yyyy-mm-dd (ej: 20280315)."
            );
            return null; // señal de error: el llamador debe abortar
        }
    }

    /**
     * Carga el archivo {@code aeropuertos.txt} desde el directorio de trabajo.
     * Devuelve la lista de aeropuertos o lanza RuntimeException si no existe.
     */
    private static AirportsLoader loadAirportsLoader() {
        Path file = Paths.get("aeropuertos.txt");
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException(
                "No se encontró aeropuertos.txt en el directorio actual: " +
                    Paths.get("").toAbsolutePath()
            );
        }
        AirportsLoader loader = new AirportsLoader();
        try {
            loader.loadFromFile(file);
        } catch (IOException e) {
            throw new RuntimeException(
                "Error al leer aeropuertos.txt: " + e.getMessage(),
                e
            );
        }
        return loader;
    }

    private static final class NetworkContext {
        private final LogisticsNetwork network;
        private final Map<String, Integer> gmtMap;

        private NetworkContext(LogisticsNetwork network, Map<String, Integer> gmtMap) {
            this.network = network;
            this.gmtMap = gmtMap;
        }
    }

    /**
     * Construye la red logística a partir de {@code aeropuertos.txt} y
     * {@code planes_vuelo.txt}.
     */
    private static NetworkContext buildNetwork(Path flightsFile)
        throws IOException {
        AirportsLoader airportsLoader = loadAirportsLoader();
        List<Airport> airports = airportsLoader.getAirports();
        Map<String, Integer> gmtMap = airportsLoader.getGmtMap() != null
            ? airportsLoader.getGmtMap()
            : Collections.emptyMap();
        RealNetworkBuilder builder = new RealNetworkBuilder(42);
        LogisticsNetwork network = builder.build(airports, flightsFile, gmtMap);
        return new NetworkContext(network, gmtMap);
    }

    // =========================================================================
    //  E1 — SIMULACIÓN DE PERIODO (datos reales, 5 días)
    // =========================================================================

    /**
     * E1: Simulación de periodo con datos reales.
     * Lee los envíos de la carpeta indicada para el rango [startDate, startDate+numDays).
     * Si {@code startDate} es {@code null}, comienza desde el primer día con datos.
     * Tiempo objetivo de ejecución: 30-90 minutos.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param startDate   fecha de inicio de la simulación; null = primera fecha en datos
     * @param numDays     número de días consecutivos a simular (mínimo 1, por defecto 5)
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runPeriodSimulation(
        Path folder,
        LocalDate startDate,
        int numDays,
        Path flightsFile
    ) {
        System.out.printf(
            "=== ESCENARIO E1: Simulación de periodo (%d días) ===%n%n",
            numDays
        );
        System.out.printf(
            "Carpeta: %s | Fecha inicio: %s | Días: %d%n%n",
            folder,
            startDate != null ? startDate : "(inicio de datos)",
            numDays
        );

        long wallStart = System.currentTimeMillis();

        // ----- Archivos de salida -----
        Path outPathMachine = buildOutputPath(
            "E1",
            startDate,
            numDays,
            "machine",
            "csv"
        );
        Path outPathHuman = buildOutputPath(
            "E1",
            startDate,
            numDays,
            "human",
            "txt"
        );
        PrintWriter outMachine = openOutputWriter(
            outPathMachine,
            "E1",
            startDate,
            numDays
        );
        PrintWriter outHuman = openOutputWriter(
            outPathHuman,
            "E1",
            startDate,
            numDays
        );

        // ----- 1) Construir la red -----
        LogisticsNetwork network;
        Map<String, Integer> gmtMap;
        try {
            NetworkContext ctx = buildNetwork(flightsFile);
            network = ctx.network;
            gmtMap = ctx.gmtMap;
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- 2) Cargar envíos -----
        EnviosDataLoader loader = new EnviosDataLoader();
        loader.setGmtMap(gmtMap);
        try {
            if (startDate != null) {
                // El usuario indicó una fecha explícita: usarla directamente
                loader.loadFromFolder(folder, startDate, numDays);
            } else {
                // Sin fecha explícita: empezar desde el primer día con datos (offset 0)
                loader.loadFromFolder(folder, 0, numDays);
            }
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Fecha base: %s | ICAO: %d | Envíos en rango: %d%n%n",
            loader.getBaseDate(),
            loader.getIcaoCodes().size(),
            loader.getShipments().size()
        );

        // ----- 3) Resolver aeropuertos -----
        // Reemplaza los aeropuertos "fantasma" (solo ICAO) por instancias reales de la red
        List<ShipmentRequest> requests = loader.resolveAirports(network);
        System.out.printf("Envíos resueltos: %d%n%n", requests.size());

        // Fecha efectiva de inicio (puede diferir de startDate si fue null)
        LocalDate fechaEfectiva = (startDate != null)
            ? startDate
            : loader.getBaseDate();

        // ----- 4) Repartir tiempo entre días -----
        long timeBudgetPerDay = 15L * 60 * 1000;

        // ----- 5) Simular día a día -----
        for (int day = 1; day <= numDays; day++) {
            System.out.println("--- Día " + day + " de " + numDays + " ---");

            // Calcular la fecha calendario de este día de simulación
            LocalDate fechaDia = fechaEfectiva.plusDays(day - 1);

            // Calcular el offset absoluto de días desde la fecha base del loader,
            // que es lo que representa Math.floor(creationTime) para cada envío.
            long offsetEsperado =
                fechaDia.toEpochDay() - loader.getBaseDate().toEpochDay();

            // Filtrar únicamente los envíos creados en este día de calendario
            List<ShipmentRequest> dayRequests = requests
                .stream()
                .filter(
                    r ->
                        (long) Math.floor(r.getCreationTime()) == offsetEsperado
                )
                .toList();

            if (dayRequests.isEmpty()) {
                // No debería ocurrir con datos válidos; avisar en lugar de silenciar
                System.out.printf(
                    "  [AVISO] Sin envíos para %s (offset=%d). Saltando.%n",
                    fechaDia,
                    offsetEsperado
                );
                continue;
            }
            System.out.printf(
                "Envíos del día %d (%s): %d%n",
                day,
                fechaDia,
                dayRequests.size()
            );

            TabuSearchConfig config = buildE1LikeConfig(
                42L + day,
                timeBudgetPerDay
            );

            TabuSearchSolver solver = new TabuSearchSolver(
                network,
                dayRequests,
                config
            );
            solver.solve();
            solver.printReport();
            String label = "DÍA " + day + " (" + fechaDia + ")";
            if (outMachine != null) solver.writeReportMachine(
                outMachine,
                label
            );
            if (outHuman != null) solver.writeReportHuman(outHuman, label);
        }

        long elapsed = System.currentTimeMillis() - wallStart;
        System.out.printf(
            "%nTiempo total E1: %.2f minutos%n",
            elapsed / 60000.0
        );
        if (outMachine != null) {
            outMachine.printf(
                "# Tiempo total E1: %.2f minutos%n",
                elapsed / 60000.0
            );
            outMachine.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathMachine.toAbsolutePath()
            );
        }
        if (outHuman != null) {
            outHuman.printf(
                "# Tiempo total E1: %.2f minutos%n",
                elapsed / 60000.0
            );
            outHuman.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathHuman.toAbsolutePath()
            );
        }
    }

    // =========================================================================
    //  E2 — TIEMPO REAL CON REPLANIFICACIÓN
    // =========================================================================

    /**
     * E2: Operación en tiempo real.
     * Carga los envíos del primer día y simula 3 eventos de cancelación,
     * verificando que la replanificación complete en menos de 5 segundos.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runRealTimeSimulation(Path folder, Path flightsFile) {
        System.out.println("=== ESCENARIO E2: Operación en Tiempo Real ===\n");

        // ----- Archivos de salida -----
        Path outPathMachineE2 = buildOutputPath(
            "E2",
            null,
            1,
            "machine",
            "csv"
        );
        Path outPathHumanE2 = buildOutputPath("E2", null, 1, "human", "txt");
        PrintWriter outMachineE2 = openOutputWriter(
            outPathMachineE2,
            "E2",
            null,
            1
        );
        PrintWriter outHumanE2 = openOutputWriter(
            outPathHumanE2,
            "E2",
            null,
            1
        );

        // ----- Red -----
        LogisticsNetwork network;
        Map<String, Integer> gmtMap;
        try {
            NetworkContext ctx = buildNetwork(flightsFile);
            network = ctx.network;
            gmtMap = ctx.gmtMap;
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos (primer día) -----
        EnviosDataLoader loader = new EnviosDataLoader();
        loader.setGmtMap(gmtMap);
        try {
            loader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        List<ShipmentRequest> requests = loader.resolveAirports(network);
        System.out.printf("Envíos cargados: %d%n%n", requests.size());

        // ----- Planificación inicial -----
        TabuSearchConfig config = new TabuSearchConfig()
            .maxIterations(200)
            .tabuTenure(10)
            .neighborhoodSize(15)
            .maxHops(2)
            .timeLimitMs(5000)
            .seed(42);

        TabuSearchSolver solver = new TabuSearchSolver(
            network,
            requests,
            config
        );
        Solution solution = solver.solve();
        solver.printReport();
        if (outMachineE2 != null) solver.writeReportMachine(
            outMachineE2,
            "INICIAL"
        );
        if (outHumanE2 != null) solver.writeReportHuman(outHumanE2, "INICIAL");

        // ----- Eventos de cancelación -----
        System.out.println("\n--- Simulando eventos dinámicos ---");
        List<Flight> flights = network.getFlights();

        for (int event = 1; event <= 3; event++) {
            Flight toCancel = flights.get((event * 7) % flights.size());
            System.out.println(
                "\nEvento " + event + ": Cancelando vuelo " + toCancel.getId()
            );

            long replanStart = System.currentTimeMillis();
            solution = solver.replanify(solution, toCancel.getId());
            long replanTime = System.currentTimeMillis() - replanStart;

            System.out.printf(
                "Tiempo de replanificación: %d ms (límite: 5000 ms) %s%n",
                replanTime,
                replanTime < 5000 ? "OK" : "¡EXCEDIDO!"
            );
            solver.printReport();
            String label =
                "EVENTO " + event + " (vuelo=" + toCancel.getId() + ")";
            if (outMachineE2 != null) solver.writeReportMachine(
                outMachineE2,
                label
            );
            if (outHumanE2 != null) solver.writeReportHuman(outHumanE2, label);
        }

        if (outMachineE2 != null) {
            outMachineE2.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathMachineE2.toAbsolutePath()
            );
        }
        if (outHumanE2 != null) {
            outHumanE2.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathHumanE2.toAbsolutePath()
            );
        }
    }

    // =========================================================================
    //  E3 — SIMULACIÓN HASTA EL COLAPSO
    // =========================================================================

    /**
     * E3: Simulación hasta el colapso.
     * Parte de los envíos reales y duplica la carga progresivamente (+20%/día)
     * hasta que el semáforo alcanza ROJO o se superen 30 días.
     *
     * @param folder      carpeta con archivos {@code _envios_XXXX_.txt}
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runCollapseSimulation(Path folder, Path flightsFile) {
        System.out.println(
            "=== ESCENARIO E3: Simulación hasta el colapso ===\n"
        );

        // ----- Archivos de salida -----
        Path outPathMachineE3 = buildOutputPath(
            "E3",
            null,
            0,
            "machine",
            "csv"
        );
        Path outPathHumanE3 = buildOutputPath("E3", null, 0, "human", "txt");
        PrintWriter outMachineE3 = openOutputWriter(
            outPathMachineE3,
            "E3",
            null,
            0
        );
        PrintWriter outHumanE3 = openOutputWriter(
            outPathHumanE3,
            "E3",
            null,
            0
        );

        // ----- Red -----
        LogisticsNetwork network;
        Map<String, Integer> gmtMap;
        try {
            NetworkContext ctx = buildNetwork(flightsFile);
            network = ctx.network;
            gmtMap = ctx.gmtMap;
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos base (primer día disponible) -----
        EnviosDataLoader loader = new EnviosDataLoader();
        loader.setGmtMap(gmtMap);
        try {
            loader.loadFromFolder(folder, 0, 1);
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            return;
        }
        List<ShipmentRequest> baseRequests = loader.resolveAirports(network);
        System.out.printf("Envíos base: %d%n%n", baseRequests.size());

        TabuSearchConfig config = new TabuSearchConfig()
            .maxIterations(300)
            .tabuTenure(12)
            .neighborhoodSize(15)
            .maxHops(3)
            .timeLimitMs(120_000)
            .seed(42);

        int day = 0;
        String status = "GREEN";

        while (!status.equals("RED") && day < 30) {
            day++;
            // Escalar la demanda: +20% acumulado por día
            List<ShipmentRequest> scaledRequests = scaleRequests(
                baseRequests,
                day,
                network
            );

            System.out.println(
                "\n--- Día de Colapso " +
                    day +
                    " | Solicitudes: " +
                    scaledRequests.size() +
                    " ---"
            );

            TabuSearchSolver solver = new TabuSearchSolver(
                network,
                scaledRequests,
                config
            );
            Solution solution = solver.solve();
            status = solver.getSemaphoreStatus(solution);
            String labelE3 = "COLAPSO DÍA " + day;
            if (outMachineE3 != null) solver.writeReportMachine(
                outMachineE3,
                labelE3
            );
            if (outHumanE3 != null) solver.writeReportHuman(
                outHumanE3,
                labelE3
            );

            int totalSuitcases = solution.getTotalSuitcases();
            int lateSuitcases = solution.getLateCount();
            double latePercent =
                totalSuitcases > 0
                    ? ((100.0 * lateSuitcases) / totalSuitcases)
                    : 0;

            System.out.printf(
                "  Maletas: %d | Tarde: %d (%.1f%%) | Semáforo: %s%n",
                totalSuitcases,
                lateSuitcases,
                latePercent,
                formatSemaphore(status)
            );

            if (status.equals("AMBER")) {
                System.out.println(
                    "  ⚠ ADVERTENCIA: ¡Sistema entrando en zona de estrés!"
                );
            }
        }

        if (status.equals("RED")) {
            System.out.println(
                "\n*** COLAPSO DEL SISTEMA detectado en el día " + day + " ***"
            );
            System.out.println(
                "El sistema ya no puede cumplir los plazos de entrega."
            );
        } else {
            System.out.println(
                "\nSimulación finalizada tras " + day + " días sin colapso."
            );
        }

        if (outMachineE3 != null) {
            outMachineE3.printf(
                "# Colapso detectado en día: %s%n",
                status.equals("RED") ? String.valueOf(day) : "nunca (<=30 días)"
            );
            outMachineE3.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathMachineE3.toAbsolutePath()
            );
        }
        if (outHumanE3 != null) {
            outHumanE3.printf(
                "# Colapso detectado en día: %s%n",
                status.equals("RED") ? String.valueOf(day) : "nunca (<=30 días)"
            );
            outHumanE3.close();
            System.out.println(
                "[Salida] Cerrado: " + outPathHumanE3.toAbsolutePath()
            );
        }
    }

    /**
     * Escala la lista de solicitudes base multiplicando la cantidad de envíos
     * por {@code 1.2^day}. Los nuevos envíos son copias de los originales
     * (circulando si es necesario) con {@code creationTime} ajustado al día 0.
     */
    private static List<ShipmentRequest> scaleRequests(
        List<ShipmentRequest> base,
        int day,
        LogisticsNetwork network
    ) {
        int target = (int) (base.size() * Math.pow(1.2, day));
        List<ShipmentRequest> result = new ArrayList<>(target);
        for (int i = 0; i < target; i++) {
            ShipmentRequest original = base.get(i % base.size());
            result.add(
                new ShipmentRequest(
                    original.getId() + "_d" + day + "_" + i,
                    original.getOrigin(),
                    original.getDestination(),
                    original.getQuantity(),
                    original.getCreationTime() % 1.0 // normalizar al día 0
                )
            );
        }
        return result;
    }

    // =========================================================================
    //  EXP — EXPERIMENTACIÓN NUMÉRICA (N réplicas, seeds 1..N)
    // =========================================================================

    /**
     * Ejecuta N réplicas independientes del Tabu Search (seeds 1..N).
     * Los datos se cargan una sola vez; solo cambia la semilla entre réplicas.
     *
     * @param folder     carpeta con archivos {@code _envios_XXXX_.txt}
     * @param startDate  fecha de inicio (null = primera fecha en datos)
     * @param numDays    número de días consecutivos de datos a cargar
     * @param replicas   número de réplicas
     * @param outputCsv  nombre del archivo CSV de salida
     * @param flightsFile ruta a {@code planes_vuelo.txt}
     */
    private static void runExperiment(
        Path folder,
        LocalDate startDate,
        int numDays,
        int replicas,
        Path outputCsvPath,
        Path flightsFile
    ) {
        System.out.println("=== MODO EXP: Experimentación numérica ===");
        System.out.printf(
            "Réplicas: %d | Salida: %s%n%n",
            replicas,
            outputCsvPath
        );
        System.out.printf(
            "Carpeta: %s | Fecha inicio: %s | Días: %d%n%n",
            folder,
            startDate != null ? startDate : "(inicio de datos)",
            numDays
        );

        // ----- Red -----
        LogisticsNetwork network;
        Map<String, Integer> gmtMap;
        try {
            NetworkContext ctx = buildNetwork(flightsFile);
            network = ctx.network;
            gmtMap = ctx.gmtMap;
        } catch (Exception e) {
            System.err.println("ERROR al construir la red: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf(
            "Red: %d aeropuertos, %d vuelos%n%n",
            network.getAirportCount(),
            network.getFlightCount()
        );

        // ----- Envíos -----
        EnviosDataLoader enviosLoader = new EnviosDataLoader();
        enviosLoader.setGmtMap(gmtMap);
        try {
            if (startDate != null) {
                enviosLoader.loadFromFolder(folder, startDate, numDays);
            } else {
                enviosLoader.loadFromFolder(folder, 0, numDays);
            }
        } catch (Exception e) {
            System.err.println("ERROR al leer los envíos: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        System.out.printf(
            "Fecha base: %s | ICAO en envíos: %d | Envíos totales: %d%n%n",
            enviosLoader.getBaseDate(),
            enviosLoader.getIcaoCodes().size(),
            enviosLoader.getShipments().size()
        );

        List<ShipmentRequest> requests = enviosLoader.resolveAirports(network);
        System.out.printf(
            "Envíos resueltos para Tabu Search: %d%n%n",
            requests.size()
        );

        // ----- CSV header -----
        String header =
            "Replica,Seed,TotalRequests,TotalSuitcases,Delivered,Undelivered," +
            "LateSuitcases,TotalDelay,CapacityOverflow,WarehouseOverflow," +
            "Fitness,Semaphore,IterationsRun,Improvements,ExecutionTime";

        try (
            PrintWriter pw = new PrintWriter(
                new FileWriter(outputCsvPath.toFile())
            )
        ) {
            pw.println(header);

            for (int rep = 1; rep <= replicas; rep++) {
                long seed = rep;

                TabuSearchConfig config = buildE1LikeConfig(
                    seed,
                    15L * 60 * 1000
                );

                System.out.printf(
                    "Réplica %2d / %d (seed=%d)...%n",
                    rep,
                    replicas,
                    seed
                );
                TabuSearchSolver solver = new TabuSearchSolver(
                    network,
                    requests,
                    config
                );
                solver.solve();

                String row = rep + "," + solver.getCsvRow(seed);
                pw.println(row);
                pw.flush();

                System.out.println("    -> " + solver.getCsvRow(seed));
            }

            System.out.println(
                "\nCSV generado: " + outputCsvPath.toAbsolutePath()
            );
            System.out.println("Cabecera: " + header);
        } catch (IOException e) {
            System.err.println("ERROR al escribir CSV: " + e.getMessage());
        }
    }

    // =========================================================================
    //  UTILIDADES
    // =========================================================================

    /**
     * Configuración base de E1.
     * EXP debe reutilizar exactamente esta configuración, variando únicamente la semilla.
     */
    private static TabuSearchConfig buildE1LikeConfig(
        long seed,
        long timeLimitMs
    ) {
        return new TabuSearchConfig()
            .maxIterations(2_000)
            .tabuTenure(20)
            .maxHops(2)
            .neighborhoodRatio(0.30)
            .criticalNeighborhoodShare(0.70)
            .criticalRefreshInterval(20)
            .criticalSlackDays(0.10)
            .bottleneckUtilization(0.90)
            .stagnationIterations(1400)
            .feasibilityBaseline(true)
            .timeLimitMs(timeLimitMs)
            .seed(seed);
    }

    private static String formatSemaphore(String status) {
        return switch (status) {
            case "GREEN" -> "🟢 VERDE";
            case "AMBER" -> "🟡 ÁMBAR";
            case "RED" -> "🔴 ROJO";
            default -> status;
        };
    }
}
