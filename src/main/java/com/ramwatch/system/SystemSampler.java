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
                .map(p -> new ProcessSnapshot(p.getProcessID(), p.getName(), p.getResidentSetSize()))
                .toList();

        return SystemSnapshot.fromMemoryAndProcesses(memSnapshot, processes);
    }
}
