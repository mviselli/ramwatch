package com.ramwatch.system;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.List;

/**
 * Reads memory and process data from the operating system through OSHI.
 *
 * <p>This is the only class that talks to OSHI: everything downstream works on the
 * immutable records of this package. The OSHI handles are resolved once in the constructor
 * and reused for every cycle, so a sample costs one query and no extra setup.
 *
 * <p>Instances are not thread-safe by contract, but {@link #sample()} is meant to be called
 * from a single background thread, which is what {@link PollingService} does.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public class SystemSampler {

    /** Root OSHI handle, kept so the hardware and OS views stay alive with this sampler. */
    private final SystemInfo systemInfo;

    /** OSHI view of physical memory, queried once per cycle. */
    private final GlobalMemory memory;

    /** OSHI view of the operating system, used to list the running processes. */
    private final OperatingSystem os;

    /**
     * Resolves the OSHI handles for memory and operating system once, so that later samples
     * do not pay for the lookup.
     */
    public SystemSampler() {
        systemInfo = new SystemInfo();
        memory = systemInfo.getHardware().getMemory();
        os = systemInfo.getOperatingSystem();
    }

    /**
     * Takes one reading of memory and running processes.
     *
     * <p>Every process contributes its resident set size. Processes that die while the list
     * is being read report a negative size and are simply skipped, so a terminating process
     * never breaks a cycle. The whole snapshot shares a single timestamp.
     *
     * @return the snapshot of this cycle, never {@code null}
     */
    public SystemSnapshot sample() {
        Instant now = Instant.now();

        MemorySnapshot memSnapshot = MemorySnapshot.fromTotalAndAvailable(
                memory.getTotal(),
                memory.getAvailable(),
                now
        );

        List<ProcessSnapshot> processes = os.getProcesses().stream()
                .filter(p -> p.getResidentSetSize() >= 0)
                .map(p -> new ProcessSnapshot(
                        p.getProcessID(),
                        displayName(p.getName(), p.getCommandLine()),
                        p.getResidentSetSize()))
                .toList();

        return SystemSnapshot.fromMemoryAndProcesses(memSnapshot, processes);
    }

    /**
     * The name to show in the process table. OSHI reports the executable's file name,
     * which for version-named binaries is a bare number (a process living in
     * {@code .../claude/versions/2.1.261} shows up as "2.1.261"). When that happens the
     * command line usually carries something readable, so fall back to it.
     *
     * @param name        executable file name reported by OSHI; may be {@code null} or blank
     * @param commandLine full command line of the process; may be {@code null} or blank
     * @return the readable name, the executable name from the command line when the reported
     *         one carries no information, or {@code "unknown"} when neither does
     */
    static String displayName(String name, String commandLine) {
        if (name != null && !name.isBlank() && isInformative(name)) {
            return name;
        }
        String fromCommand = firstToken(commandLine);
        if (fromCommand != null && isInformative(fromCommand)) {
            return fromCommand;
        }
        return name == null || name.isBlank() ? "unknown" : name;
    }

    /**
     * A name carrying no letters (a version, a pid, a hash) tells the user nothing.
     *
     * @param value candidate name to judge; must not be {@code null}
     * @return {@code true} if the value contains at least one letter
     */
    private static boolean isInformative(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    /**
     * File name of the executable in the command line, ignoring its arguments.
     *
     * @param commandLine full command line; may be {@code null} or blank
     * @return the last path segment of the first token, or {@code null} when the command line
     *         carries nothing usable
     */
    private static String firstToken(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return null;
        }
        String command = commandLine.strip().split("\\s+", 2)[0];
        int slash = command.lastIndexOf('/');
        String fileName = slash < 0 ? command : command.substring(slash + 1);
        return fileName.isBlank() ? null : fileName;
    }
}
