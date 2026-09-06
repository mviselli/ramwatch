package com.ramwatch.ui;

import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.function.LongSupplier;

/**
 * Table cell showing a process's share of physical memory as a bar followed by the percentage.
 *
 * <p>The two carry deliberately different measures. The number is the share of physical
 * memory, which is what the user wants to know in absolute terms. The bar is instead drawn
 * against the heaviest process of the cycle, so the top row is always full and the others read
 * as fractions of it: on a machine where no single process holds more than a few percent of the
 * RAM, a bar scaled to the total would be three pixels wide on every row and would say nothing
 * about the shape of the distribution.
 *
 * <p>Both are computed here, from totals supplied by the view, rather than stored on the model:
 * {@link ProcessSnapshot} is a plain reading and knows nothing of the machine it was taken on
 * nor of the other processes it was ranked against.
 *
 * <p>The nodes are built once per cell and only resized afterwards, since a cell is reused for
 * many rows as the table scrolls or refreshes.
 *
 * @author Michele Viselli
 * @since 1.0
 */
final class ProcessShareCell extends TableCell<ProcessSnapshot, ProcessSnapshot> {

    /** Width of the bar at 100%, in pixels. */
    private static final double TRACK_WIDTH = 92;

    /** Width given to a share too small to be drawn to scale, in pixels. */
    private static final double MIN_VISIBLE_WIDTH = 3;

    /** Total physical memory of the latest cycle, in bytes; never zero. */
    private final LongSupplier totalBytes;

    /** Memory of the heaviest process of the latest cycle, in bytes; never zero. */
    private final LongSupplier heaviestProcessBytes;

    /** The filled part of the bar, whose width is the share. */
    private final Region fill = new Region();

    /** The unfilled bar the share is drawn over. */
    private final StackPane track = new StackPane(fill);

    /** The share of physical memory, as a percentage with one decimal. */
    private final Label label = new Label();

    /** Holds the bar and the percentage on one line. */
    private final HBox box = new HBox(10, track, label);

    /**
     * Builds the cell's nodes, ready to be filled by {@link #updateItem}.
     *
     * @param totalBytes           supplies the total physical memory the percentage is
     *                             measured against, in bytes; must never return zero
     * @param heaviestProcessBytes supplies the memory of the heaviest process of the cycle,
     *                             which the bar is scaled to, in bytes; must never return zero
     */
    ProcessShareCell(LongSupplier totalBytes, LongSupplier heaviestProcessBytes) {
        this.totalBytes = totalBytes;
        this.heaviestProcessBytes = heaviestProcessBytes;

        fill.getStyleClass().add("share-fill");
        track.getStyleClass().add("share-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.setMinWidth(TRACK_WIDTH);
        track.setPrefWidth(TRACK_WIDTH);
        track.setMaxWidth(TRACK_WIDTH);
        label.getStyleClass().add("share-percent");
        box.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Shows a process's share, or nothing when the cell holds no row.
     *
     * @param process the process to measure, or {@code null} for an empty cell
     * @param empty   whether the cell holds no row at all
     */
    @Override
    protected void updateItem(ProcessSnapshot process, boolean empty) {
        super.updateItem(process, empty);

        if (empty || process == null) {
            setGraphic(null);
            return;
        }

        long bytes = process.usedMemoryBytes();
        double ofHeaviest = Math.min(1.0, bytes / (double) heaviestProcessBytes.getAsLong());
        double width = ofHeaviest <= 0 ? 0 : Math.max(MIN_VISIBLE_WIDTH, TRACK_WIDTH * ofHeaviest);

        fill.setPrefWidth(width);
        fill.setMaxWidth(width);
        label.setText(String.format("%.1f%%", bytes * 100.0 / totalBytes.getAsLong()));
        setGraphic(box);
    }
}
