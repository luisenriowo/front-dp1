# AGENTS.md — tabu-search-tasf

Java 17 Maven project. Tabu Search metaheuristic for luggage delivery scheduling (PUCP 2026-1).

## Build & Run

```bash
mvn clean compile
```

**Always pass `-Dfile.encoding=UTF-8`** when running manually — the program sets UTF-8 stdout and uses accented chars/box-drawing:
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1 ./_envios_preliminar_
```

`aeropuertos.txt` and `planes_vuelo.txt` must be present in the working directory (or passed via `--flights`).

## Execution Modes

All modes accept flags after the folder argument. Flags use `--key=value` or `--key value` syntax.

| Mode | Flags | Description |
|------|-------|-------------|
| `E1` | `--start=yyyymmdd` `--days=5` `--flights=path` | Period sim from real `_envios_XXXX_.txt` files (default: 5 days from first date in data) |
| `E2` | `--flights=path` | Real-time replanning (≤5 s/event) |
| `E3` | `--flights=path` | Collapse sim — demand +20%/day until RED |
| `EXP` | `--replicas=30` `--output=file.csv` `--flights=path` | N replicas (seeds 1..N), writes CSV incrementally |

E1 example — all defaults (5 days, first date in data, planes_vuelo.txt from cwd):
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1 ./_envios_preliminar_
```

E1 example — explicit flags:
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main E1 ./_envios_preliminar_ --start=20260102 --days=5
```

EXP example:
```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.Main EXP ./_envios_preliminar_ --replicas=30 --output=E1_30_runs_TS.csv
```

Debug (attach on port 5005):
```bash
java -Dfile.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  -cp target/classes pe.edu.pucp.tasf.Main E1 ./_envios_preliminar_ --start=20260102 --days=5
```

## Testing

**No JUnit.** The only test is a hand-rolled integration test at `src/main/java/pe/edu/pucp/tasf/test/RunnerTest.java` (compiled with production code, lives under `src/main/java/`).

```bash
java -Dfile.encoding=UTF-8 -cp target/classes pe.edu.pucp.tasf.test.RunnerTest [folder]
```
- Default folder: `test-data/`
- Pass `_envios_preliminar_/` for real data
- Exits with code `1` on failure
- Runs 50 TS iterations, 30 s time limit

## Architecture

```
Main.java                        — entry point, dispatches 4 modes (E1/E2/E3/EXP)
model/                           — Airport, Flight (mutable load), LogisticsNetwork, Solution, ShipmentRequest
algorithm/TabuSearchSolver.java  — solve(), replanify(), getCsvRow()
algorithm/TabuSearchConfig.java  — fluent builder for TS parameters
io/EnviosDataLoader.java         — parser for _envios_XXXX_.txt
io/AirportsLoader.java           — parser for aeropuertos.txt (UTF-16)
util/RealNetworkBuilder.java     — network from Airport list + planes_vuelo.txt
test/RunnerTest.java             — integration smoke test
```

## Key Quirks

- **`Flight` is mutable** — tracks assigned load. `LogisticsNetwork.resetLoads()` must be called before each `solve()` call. EXP mode does this between replicas. Virtual next-day flights (e.g. `F5_d1`) are now cached in `LogisticsNetwork.virtualFlights` and also reset by `resetLoads()`.
- **`aeropuertos.txt` is required** — loaded by `AirportsLoader` to build the network. Must be in the working directory.
- **`planes_vuelo.txt` is required** — loaded by `RealFlightLoader`. Must be in the working directory or passed via `--flights`. There is no synthetic fallback.
- **Shipment filename encodes origin ICAO**: `_envios_SPIM_.txt` = Lima (SPIM).
- **Default seed is `42`**; EXP mode uses seeds 1..N for reproducibility.
- **No CI, no linter, no pre-commit hooks.**

## Input Data

Shipment file line format: `ID-YYYYMMDD-HH-MM-DEST_ICAO-QTY-CLIENT_ID`
Example: `000000001-20260102-00-47-SUAA-002-0032535`

ICAO prefix → continent (used for time windows: 1 day intra, 2 days inter):
- `S, K, C, M, T` → AMERICA
- `E, L, B` → EUROPE
- `O, U, V, R, Z, W, Y` → ASIA
