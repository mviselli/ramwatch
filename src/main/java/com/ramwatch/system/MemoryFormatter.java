package com.ramwatch.system;

/**
 * Turns raw byte counts and ratios into the strings shown in the UI.
 *
 * <p>Formatting is kept out of the snapshot records on purpose: the models hold numbers,
 * this utility decides how they read. Sizes below one gigabyte are shown in whole megabytes,
 * larger ones in gigabytes with two decimals, and percentages with one decimal.
 *
 * <p>This class is stateless and cannot be instantiated.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class MemoryFormatter {

    /** Bytes in one gigabyte (1024<sup>3</sup>). */
    private static final long GB = 1_073_741_824L;

    /** Bytes in one megabyte (1024<sup>2</sup>). */
    private static final long MB = 1_048_576L;

    /** Utility class: not meant to be instantiated. */
    private MemoryFormatter() {}

    /**
     * Formats a size for display, choosing the unit from its magnitude.
     *
     * @param bytes size to format, in bytes
     * @return the size in gigabytes with two decimals when it reaches one gigabyte,
     *         otherwise in whole megabytes (for example {@code "3.25 GB"} or {@code "512 MB"})
     */
    public static String formatBytes(long bytes) {
        if (bytes >= GB) {
            return String.format("%.2f GB", (double) bytes / GB);
        }
        return (bytes / MB) + " MB";
    }

    /**
     * Formats a ratio as a percentage with one decimal.
     *
     * @param ratio value in the range 0.0 to 1.0, where {@code 1.0} means 100%
     * @return the percentage followed by the percent sign, for example {@code "72.4%"}
     */
    public static String formatPercent(double ratio) {
        return String.format("%.1f%%", ratio * 100);
    }

    /**
     * Computes the fraction of physical memory in use in a reading.
     *
     * @param snapshot the memory reading to measure; must not be {@code null}
     * @return the used fraction, from 0.0 (nothing used) to 1.0 (fully used)
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public static double usageRatio(MemorySnapshot snapshot) {
        return (double) snapshot.usedBytes() / snapshot.totalBytes();
    }
}
