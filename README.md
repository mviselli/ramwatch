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

## Build

```bash
mvn clean package
```

This produces `target/ramwatch-0.1.0-SNAPSHOT.jar` plus its runtime dependencies in
`target/lib`. The jar's manifest names `com.ramwatch.Launcher` as the main class and lists
`lib/` on its class path, so the build output is runnable as it stands.

## Run

During development:

```bash
mvn javafx:run
```

From the built jar, with no further arguments:

```bash
java -jar target/ramwatch-0.1.0-SNAPSHOT.jar
```

JavaFX is loaded from the class path rather than the module path, which is why the entry
point is `Launcher` and not `RamWatchApp`: a main class extending `javafx.application.Application`
refuses to start unless JavaFX is a named module. The trade-off is one warning at startup,
`Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`, which is
cosmetic — it is the price of a jar that runs without a `--module-path` argument.

Either way the process is a plain JVM, so macOS labels it "java" in the menu bar and the
dock. The app bundle below is what gives it its own name and icon.

## macOS app bundle

```bash
tools/package-mac.sh          # builds target/dist/RamWatch.app
open target/dist/RamWatch.app
```

The script gathers the jar and its dependencies, renders the icon at every size macOS asks
for, and calls `jpackage --type app-image`. The result is a self-contained `RamWatch.app`
carrying its own Java runtime: it starts under the name RamWatch, with the RamWatch icon,
and needs no JDK installed to run.

### Cross-platform limits

`jpackage` builds only for the platform it runs on: there is no cross-compilation. The
script is macOS-only by construction — it calls `iconutil` and produces an `.app` — and the
project has been packaged and tested on macOS on Apple Silicon only.

The application code itself is portable. JavaFX and OSHI both cover Windows and Linux, and
nothing outside `tools/package-mac.sh` is macOS-specific, so `mvn clean package` and
`java -jar` are expected to work on the other two platforms; they have not been verified
there. Packaging for Windows (`--type msi`, needs WiX) or Linux (`--type deb` or `rpm`)
means running `jpackage` on those systems with an equivalent script and an icon in the
native format (`.ico`, `.png`).

One dependency detail matters when building elsewhere: the JavaFX artifacts are resolved
with a platform classifier (`mac-aarch64` here). Maven picks the classifier from the build
machine, so a build on Windows or Linux pulls the right natives without any change to
`pom.xml` — but the contents of `target/lib`, and therefore any bundle made from it, are
tied to the platform that built them.

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
