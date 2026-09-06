# RamWatch

Lightweight Java desktop utility for real-time RAM monitoring and process diagnostics.

RamWatch observes and reports: it shows how much memory is in use, which processes are
using it, and how the usage evolves. It never terminates processes and never tries to free
memory.

![RamWatch dark theme](media/dark_theme.png)

## Features

- Total, used and available RAM, refreshed live, with the used percentage on a ring gauge.
- `STABLE`, `WARNING` and `CRITICAL` states, from configurable free-memory thresholds.
- Top-process table with name, PID, memory and share of total RAM, sorted by consumption.
- Configurable minimum process size, to keep small processes out of the table.
- RAM history chart over a bounded 300-sample buffer.
- Optional event log, written on state changes only, with CSV export.
- Configurable polling interval, minimum one second.
- Settings dialog with validation, persisted to `~/.ramwatch/config.properties`.
- Light and dark themes.

## Tech stack

Java 21, JavaFX 21, OSHI 6.6.5, Maven, JUnit 5, `jpackage` for the macOS bundle.

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer

## Build

```bash
mvn clean package
```

Produces `target/ramwatch-0.1.0-SNAPSHOT.jar` and its runtime dependencies in `target/lib`.
The manifest names `com.ramwatch.Launcher` as the main class and lists `lib/` on the class
path.

## Run

```bash
mvn javafx:run                                  # from source
java -jar target/ramwatch-0.1.0-SNAPSHOT.jar    # from the built jar
```

JavaFX is loaded from the class path, not the module path, which is why the entry point is
`Launcher` rather than `RamWatchApp`. Startup prints one JavaFX warning about the unnamed
module; it is harmless.

Both commands run a plain JVM, which macOS labels "java". The app bundle below carries the
application name and icon.

## macOS app bundle

```bash
tools/package-mac.sh          # builds target/dist/RamWatch.app
open target/dist/RamWatch.app
```

The script collects the jar and its dependencies, renders the icon, and runs
`jpackage --type app-image`. The bundle embeds its own Java runtime and needs no JDK.

Packaging limits:

- `jpackage` does not cross-compile: each platform must be built on itself.
- The script is macOS-only (`iconutil`, `.app`). Windows (`--type msi`) and Linux
  (`--type deb`/`rpm`) need an equivalent script.
- JavaFX resolves with a platform classifier, so `target/lib` is tied to the build machine.
- Built and tested on macOS, Apple Silicon only. The application code has no macOS-specific
  parts, but Windows and Linux are unverified.

## Test

```bash
mvn test                              # all tests
mvn test -Dtest=SystemSamplerTest     # single class
```

112 tests covering the system models, the OSHI sampler, the polling service, memory analysis
and state tracking, the event log and CSV export, configuration persistence and the
table-refresh logic. The OSHI tests need to write JNA's native library to a temporary
directory and fail under a sandbox that forbids it.

Maven stores dependencies in `.m2/repository` inside the project, configured through
`.mvn/maven.config`.

## Performance

Measured on a MacBook Air (Apple Silicon, 8 cores, 8 GB RAM), macOS 27.0, OpenJDK 25, on a
system with about 680 processes, with `ps` and `jcmd` over 12 five-second windows after a
40-second warmup.

| Metric | Result |
|---|---|
| CPU at 1 s polling | 12.6% of one core (~1.6% of an 8-core machine) |
| CPU at 5 s / 30 s polling | 3.8% / 1.2% of one core |
| CPU between cycles | 0.0% |
| Heap live set | 24-43 MB |
| Resident set (RSS) | 260-315 MB, mostly JavaFX runtime and the JVM's default heap reservation |

A polling cycle is dominated by enumerating every process on the system. The chart series
doubles as its circular buffer, and the process table is refreshed row by row against what
is on screen, which removes about 80% of cell redraws at 1 s polling.

## Screenshots

Light theme:

![RamWatch light theme](media/light_theme.png)

## Architecture

```text
OSHI → system snapshots → analysis → JavaFX dashboard
                                  ↘ storage (optional event log → CSV)
```

The boundary between layers is a set of immutable records — `MemorySnapshot`,
`ProcessSnapshot`, `SystemSnapshot` — each describing one polling cycle.

- `src/main/java/com/ramwatch/system`: OSHI integration, sampling, data models, formatting
- `src/main/java/com/ramwatch/analysis`: thresholds, RAM states, process sorting and filtering
- `src/main/java/com/ramwatch/ui`: dashboard, gauge, chart, process table, themes, settings
- `src/main/java/com/ramwatch/config`: user preferences, defaults and persistence
- `src/main/java/com/ramwatch/storage`: event log, log reader and CSV export
- `src/test/java`: unit tests for all of the above

The analysis, storage and configuration layers do not depend on JavaFX. `ProcessRowDiff`,
which decides which table rows changed enough to redraw, touches no scene graph and is unit
tested on its own.

## License

MIT. See [LICENSE](LICENSE).
