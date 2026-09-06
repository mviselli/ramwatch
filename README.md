# RamWatch

RamWatch is a lightweight Java desktop utility for real-time RAM monitoring and process diagnostics.

It is designed as an observation and diagnostic tool, not as an aggressive RAM booster. The application shows current memory usage, identifies the processes using the most memory, and keeps polling controlled so that the monitor itself remains lightweight.

## Current status

The monitoring dashboard MVP is implemented and working. The current version includes:

- real-time total, used and available RAM metrics;
- used-memory percentage and `STABLE`, `WARNING` and `CRITICAL` states;
- configurable polling interval with a minimum of one second;
- process list sorted by memory usage;
- configurable minimum process-memory filter;
- top-process table with PID, memory and percentage of total RAM;
- RAM usage history chart with a bounded 300-sample buffer;
- light and dark themes;
- settings dialog with validation;
- persistent preferences loaded from `~/.ramwatch/config.properties`;
- background polling with clean shutdown on application close.

The logging toggle is already part of the configuration model and UI, but event logging and CSV export are still planned.

## Tech stack

- Java 21
- JavaFX 21
- OSHI 6.6.5
- Maven
- JUnit 5

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer

## Run

```bash
mvn javafx:run
```

## Build

```bash
mvn clean package
```

## macOS app bundle

Run from Maven, the app is just a JVM process: macOS labels it "java" and gives it a generic
icon. Bundling it fixes both.

```bash
tools/package-mac.sh          # builds target/dist/RamWatch.app
open target/dist/RamWatch.app
```

## Test

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=SystemSamplerTest
```

The current test suite covers the system models, OSHI sampler, polling service, memory analysis, formatting and configuration persistence.

Maven is configured through `.mvn/maven.config` to store dependencies in `.m2/repository` inside this project. This avoids relying on the default `~/.m2` path.

## Screenshots

![RamWatch dark theme](media/dark_theme.png)

![RamWatch light theme](media/light_theme.png)

## Performance

RamWatch is meant to stay open, so its own footprint was measured rather than assumed.
The summary:

| Metric | Result |
|---|---|
| CPU at the 1 s minimum polling interval | 12.6% of one core (~1.6% of an 8-core machine) |
| CPU at 5 s / 30 s polling | 3.8% / 1.2% of one core |
| CPU between polling cycles | 0.0% - the idle UI costs nothing measurable |
| Heap live set (used heap after GC) | 24-43 MB, under the 50 MB target |
| Resident set (RSS) | 260-315 MB, dominated by the JavaFX runtime and the JVM's default heap reservation |

Measured on a MacBook Air (Apple Silicon, 8 cores, 8 GB RAM), macOS 27.0, OpenJDK 25,
on a system with about 680 running processes, using `ps` and `jcmd` over 12 five-second
windows after a 40-second warmup.

The 50 MB target originally applied to RSS. It was missed by five times and redefined as
a target on the application's live set, because the resident set of a JavaFX process is
dominated by fixed costs the application code does not control.

Two optimisations came out of the profiling work: the chart series is now its own
circular buffer (one allocation per cycle instead of about 300), and the process table is
refreshed row by row instead of being rebuilt, which removes 80.9% of cell redraws at 1 s
polling. Neither moves CPU measurably: a cycle is dominated by enumerating every process
on the system, not by drawing. That enumeration remains the real bottleneck.

## Architecture

The data flow is:

```text
OSHI → system snapshots → analysis → JavaFX dashboard
```

## Project structure

- `src/main/java/com/ramwatch/system`: OSHI integration, sampling, immutable data models and formatting
- `src/main/java/com/ramwatch/analysis`: thresholds, RAM states, process sorting and filtering
- `src/main/java/com/ramwatch/ui`: JavaFX dashboard, chart, process table, themes and settings dialog
- `src/main/java/com/ramwatch/config`: user preferences, defaults and persistence
- `src/main/java/com/ramwatch/storage`: reserved for local event logs and CSV export
- `src/test/java`: unit tests for the core, analysis and configuration layers

## Roadmap

Next planned work:

1. Implement critical-event logging with timestamps, RAM metrics and top consumer.
2. Avoid repeated alerts while the RAM state remains unchanged.
3. Add optional CSV export.
4. Prepare platform packaging with `jpackage`.

RamWatch should remain focused on lightweight monitoring and diagnosis. Automatic process termination or aggressive memory “optimization” is intentionally outside the MVP scope.
