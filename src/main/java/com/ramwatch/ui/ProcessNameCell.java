package com.ramwatch.ui;

import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Table cell showing a process name preceded by a dot that marks the heaviest consumer.
 *
 * <p>The ranking is already the order of the rows, but the top consumer deserves to be
 * recognisable at a glance rather than inferred from the position, so the first row's dot takes
 * the accent colour and the others a muted one.
 *
 * <p>The two nodes are built once per cell and only restyled afterwards: a cell is reused for
 * many rows as the table scrolls or refreshes, so building them in {@code updateItem} would
 * allocate on every redraw.
 *
 * @author Michele Viselli
 * @since 1.0
 */
final class ProcessNameCell extends TableCell<ProcessSnapshot, String> {

    /** Diameter of the rank dot, in pixels. */
    private static final double DOT_SIZE = 7;

    /** The rank dot, accent-coloured on the first row. */
    private final Region dot = new Region();

    /** The process name. */
    private final Label label = new Label();

    /** Holds the dot and the name on one line. */
    private final HBox box = new HBox(9, dot, label);

    /**
     * Builds the cell's nodes, ready to be filled by {@link #updateItem}.
     */
    ProcessNameCell() {
        dot.setMinSize(DOT_SIZE, DOT_SIZE);
        dot.setPrefSize(DOT_SIZE, DOT_SIZE);
        dot.setMaxSize(DOT_SIZE, DOT_SIZE);
        dot.getStyleClass().add("rank-dot");
        label.getStyleClass().add("process-name");
        box.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Shows a process name, or nothing when the cell holds no row.
     *
     * @param name  the process name, or {@code null} for an empty cell
     * @param empty whether the cell holds no row at all
     */
    @Override
    protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);

        if (empty || name == null) {
            setGraphic(null);
            return;
        }

        label.setText(name);
        dot.getStyleClass().remove("rank-top");
        if (getIndex() == 0) {
            dot.getStyleClass().add("rank-top");
        }
        setGraphic(box);
    }
}
