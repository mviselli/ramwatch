package com.ramwatch.ui;

import com.ramwatch.analysis.RamState;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.geometry.Pos;

/**
 * Ring gauge showing the share of memory in use, with the percentage and the state at its centre.
 *
 * <p>JavaFX has no ring gauge of its own, so the dial is two stacked {@link Arc}s: a full
 * circle acting as the track and a second arc, drawn clockwise from the top, whose sweep is the
 * used share. Both are stroked and unfilled, which is what makes the ring a ring; their colours
 * come from the stylesheets, so the gauge follows the theme like every other node.
 *
 * <p>Everything is built once. {@link #update} only changes the sweep of one arc and the text of
 * two labels, and the state styling is reapplied only when the state actually changes, so a
 * polling cycle costs nothing beyond that.
 *
 * <p>Like every JavaFX node, it must only be touched on the application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
final class RamGauge extends StackPane {

    /** Outer radius of the ring, in pixels. */
    private static final double RADIUS = 58;

    /** Thickness of the ring, in pixels. */
    private static final double THICKNESS = 13;

    /** A full turn, in degrees; the sweep of a gauge reading 100%. */
    private static final double FULL_TURN = 360;

    /** Twelve o'clock, where the arc starts. */
    private static final double TOP_OF_THE_DIAL = 90;

    /** The unfilled circle the progress arc runs over. */
    private final Arc track = arc();

    /** The filled part of the ring, swept clockwise from the top. */
    private final Arc progress = arc();

    /** Usage percentage, at the centre of the ring. */
    private final Label lblPercent = new Label("0.0%");

    /** Current state, under the percentage. */
    private final Label lblState = new Label();

    /** State the styling reflects; {@code null} until the first update. */
    private RamState shownState;

    /**
     * Builds an empty gauge reading zero.
     */
    RamGauge() {
        track.getStyleClass().add("gauge-track");
        progress.getStyleClass().add("gauge-progress");
        progress.setLength(0);

        lblPercent.getStyleClass().add("gauge-percent");
        lblState.getStyleClass().add("gauge-state");

        VBox readout = new VBox(0, lblPercent, lblState);
        readout.setAlignment(Pos.CENTER);

        setAlignment(Pos.CENTER);
        setMinSize(RADIUS * 2 + THICKNESS, RADIUS * 2 + THICKNESS);
        setPrefSize(RADIUS * 2 + THICKNESS, RADIUS * 2 + THICKNESS);
        setMaxSize(RADIUS * 2 + THICKNESS, RADIUS * 2 + THICKNESS);
        getStyleClass().add("gauge");
        getChildren().addAll(track, progress, readout);
    }

    /**
     * Points the gauge at a new reading.
     *
     * @param usedPercent share of memory in use, from 0 to 100
     * @param state       the state to colour the ring with; must not be {@code null}
     */
    void update(double usedPercent, RamState state) {
        // Negative, because a positive length would sweep the arc anticlockwise.
        progress.setLength(-FULL_TURN * usedPercent / 100.0);
        lblPercent.setText(usedPercent + "%");

        if (state != shownState) {
            shownState = state;
            applyStateStyle(state);
        }
    }

    /**
     * Recolours the ring and the state label for the given state.
     *
     * <p>The previous state's classes are removed first, so the styles never stack up.
     *
     * @param state the state to reflect; must not be {@code null}
     */
    private void applyStateStyle(RamState state) {
        String key = switch (state) {
            case STABLE -> "stable";
            case WARNING -> "warning";
            case CRITICAL -> "critical";
        };

        progress.getStyleClass().removeAll("stable", "warning", "critical");
        progress.getStyleClass().add(key);
        lblState.getStyleClass().removeAll("state-stable", "state-warning", "state-critical");
        lblState.getStyleClass().add("state-" + key);
        lblState.setText(state.name());
    }

    /**
     * Builds one of the two arcs, sized and stroked but not yet styled.
     *
     * @return an open, unfilled arc starting at the top of the dial
     */
    private static Arc arc() {
        Arc arc = new Arc(0, 0, RADIUS, RADIUS, TOP_OF_THE_DIAL, -FULL_TURN);
        arc.setType(ArcType.OPEN);
        arc.setStrokeWidth(THICKNESS);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        return arc;
    }
}
