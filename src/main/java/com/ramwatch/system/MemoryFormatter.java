package com.ramwatch.system;

public final class MemoryFormatter {

    private static final long GB = 1_073_741_824L;
    private static final long MB = 1_048_576L;

    private MemoryFormatter() {}

    public static String formatBytes(long bytes) {
        if (bytes >= GB) {
            return String.format("%.2f GB", (double) bytes / GB);
        }
        return (bytes / MB) + " MB";
    }

    public static String formatPercent(double ratio) {
        return String.format("%.1f%%", ratio * 100);
    }

    public static double usageRatio(MemorySnapshot snapshot) {
        return (double) snapshot.usedBytes() / snapshot.totalBytes();
    }
}
