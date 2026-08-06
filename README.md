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
4. Profile RAM and CPU usage of the application itself.
5. Prepare platform packaging with `jpackage`.

RamWatch should remain focused on lightweight monitoring and diagnosis. Automatic process termination or aggressive memory “optimization” is intentionally outside the MVP scope.
