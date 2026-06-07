# RamWatch

RamWatch is a lightweight Java desktop utility for real-time RAM monitoring with process tracking and alerts.

The project is designed as a monitoring and diagnostic tool, not as an aggressive RAM booster. Its goal is to show current memory usage, identify top memory-consuming processes, and keep the application itself lightweight enough for machines with moderate resources.

## Tech Stack
- Java 21
- JavaFX
- OSHI
- Maven

## Requirements
- JDK 21 or newer
- Maven 3.9 or newer

## Run
```bash
mvn javafx:run -Dmaven.repo.local=.m2/repository
```

## Build
```bash
mvn clean package -Dmaven.repo.local=.m2/repository
```

## Test
```bash
# All tests
mvn test -Dmaven.repo.local=.m2/repository

# Single test class
mvn test -Dtest=SystemSamplerTest -Dmaven.repo.local=.m2/repository
```

> The `-Dmaven.repo.local=.m2/repository` flag is required because the local Maven repository is stored inside the project directory rather than the default `~/.m2`.

## Project Structure
- `src/main/java/com/ramwatch/system`: system sampling, OSHI integration, data models and formatting
- `src/main/java/com/ramwatch/analysis`: thresholds, sorting, filtering and state analysis
- `src/main/java/com/ramwatch/ui`: JavaFX screens and controls
- `src/main/java/com/ramwatch/config`: user preferences and defaults
- `src/main/java/com/ramwatch/storage`: local logs and future CSV export

## Current Status
**Phase 0 — complete:** Maven build, Java 21 target, JavaFX and OSHI dependencies, package structure, minimal JavaFX shell.

**Phase 1 — complete:** core monitoring layer.
- `SystemSampler`: reads real RAM and process data from the OS via OSHI and produces a `SystemSnapshot` on each call.
- `MemoryFormatter`: converts raw byte values to human-readable MB/GB strings and formats usage percentages.
- `PollingService`: background thread (min 1 s interval, daemon) that calls `SystemSampler` at a fixed rate and delivers snapshots to a listener; shuts down cleanly on `stop()`.
