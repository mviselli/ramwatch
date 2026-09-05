package com.ramwatch.system;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.time.Instant;
import java.util.List;

public class SystemSampler {

    private final SystemInfo systemInfo;
    private final GlobalMemory memory;
    private final OperatingSystem os;

    public SystemSampler() {
        systemInfo = new SystemInfo();
        memory = systemInfo.getHardware().getMemory();
        os = systemInfo.getOperatingSystem();
    }

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

    /** A name carrying no letters (a version, a pid, a hash) tells the user nothing. */
    private static boolean isInformative(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    /** File name of the executable in the command line, ignoring its arguments. */
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
