package com.ramwatch.ui;

import com.ramwatch.system.ProcessSnapshot;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Brings a list of table rows in line with a cycle, touching only what changed on screen.
 *
 * <p>Replacing the whole list at every cycle would make {@code TableView} discard and rebuild
 * every cell it shows, even when the same processes are ranked in the same order and their
 * memory moved by a few kilobytes. This class replaces rows one by one, and only those whose
 * appearance would actually change.
 *
 * <p>Position, not PID, identifies a row: the ranking is what the table shows, so a process
 * that moves up or down the list must move on screen too.
 *
 * <p>It holds no state and knows nothing of the scene graph, but it drives an observable list
 * a table is bound to, so it must be called on the JavaFX application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
final class ProcessRowDiff {

    /**
     * Memory change below which a row is left untouched, in bytes.
     *
     * <p>The table prints whole megabytes below one gigabyte and two decimals of a gigabyte
     * above it, so a drift of less than a megabyte cannot alter a single character on screen.
     * The comparison is always against the value <em>displayed</em>, never the previous
     * reading, so a slow drift still redraws the row as soon as it accumulates to a megabyte:
     * what a row shows is never more than one megabyte behind the truth.
     */
    static final long VISIBLE_MEMORY_DELTA_BYTES = 1_048_576L;

    /** Utility class: not meant to be instantiated. */
    private ProcessRowDiff() {}

    /**
     * Updates the rows in place so they match the latest ranking.
     *
     * <p>Rows are matched by position and each one is replaced only when
     * {@link #isVisiblyDifferent} says its appearance would change; the tail is then extended
     * or trimmed so the list holds exactly as many rows as the cycle reports.
     *
     * @param rows   the rows currently displayed, modified in place; must not be {@code null}
     * @param latest the ranked processes of the latest cycle; must not be {@code null}
     * @return how many rows were replaced, added or removed, which is what the table redraws
     */
    static int apply(ObservableList<ProcessSnapshot> rows, List<ProcessSnapshot> latest) {
        int touched = 0;

        int shared = Math.min(rows.size(), latest.size());
        for (int i = 0; i < shared; i++) {
            ProcessSnapshot fresh = latest.get(i);
            if (isVisiblyDifferent(rows.get(i), fresh)) {
                rows.set(i, fresh);
                touched++;
            }
        }

        if (rows.size() > latest.size()) {
            touched += rows.size() - latest.size();
            rows.remove(latest.size(), rows.size());
        } else if (rows.size() < latest.size()) {
            touched += latest.size() - rows.size();
            rows.addAll(latest.subList(rows.size(), latest.size()));
        }

        return touched;
    }

    /**
     * Whether redrawing a row with a fresh reading would change what it shows.
     *
     * <p>A different process is always a different row. The same process counts as unchanged
     * while its memory stays within {@value #VISIBLE_MEMORY_DELTA_BYTES} bytes of the value
     * already on screen, which is finer than the table can print.
     *
     * @param shown the reading the row currently displays; must not be {@code null}
     * @param fresh the reading from the latest cycle; must not be {@code null}
     * @return {@code true} when the row must be replaced
     */
    static boolean isVisiblyDifferent(ProcessSnapshot shown, ProcessSnapshot fresh) {
        return shown.pid() != fresh.pid()
                || !shown.name().equals(fresh.name())
                || Math.abs(shown.usedMemoryBytes() - fresh.usedMemoryBytes()) >= VISIBLE_MEMORY_DELTA_BYTES;
    }
}
