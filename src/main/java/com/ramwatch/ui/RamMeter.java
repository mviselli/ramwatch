package com.ramwatch.ui;

import com.ramwatch.analysis.RamState;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Horizontal meter showing the share of memory in use: the reading on one line, the bar under it.
 *
 * <p>It replaces the ring gauge the header used to carry. A ring spends a square of space to say
 * one number, which cost the header most of its height; a bar says the same thing across a strip
 * a few pixels tall and reads left to right like the chart below it.
 *
 * <p>The bar is two stacked regions — a full-width track and a fill whose width is bound to the
 * used share — so a new reading moves one binding rather than rebuilding anything. The colours
 * live in the stylesheets, so the meter follows the theme like every other node.
 *
 * <p>Everything is built once. {@link #update} writes one label and one number, and reapplies the
 * state styling only when the state actually changes.
 *
 * <p>Like every JavaFX node, it must only be touched on the application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
final class RamMeter extends VBox {

    /** Height of the bar, in pixels. */
    private static final double BAR_HEIGHT = 10;

    /** Share of the track the fill covers, from 0 to 1; the fill's width is bound to it. */
    private final SimpleDoubleProperty fraction = new SimpleDoubleProperty(0);

    /** Usage percentage, shown large at the left of the reading row. */
    private final Label lblPercent = new Label("0.0%");

    /** Fixed caption naming what the meter measures. */
    private final Label lblCaption = new Label("IN USE");

    /** Current state, shown as a tinted pill beside the percentage. */
    private final Label lblState = new Label();

    /** The coloured part of the bar, as wide as {@link #fraction} of the track. */
    private final Region fill = new Region();

    /** State the styling reflects; {@code null} until the first update. */
    private RamState shownState;

    /**
     * Builds an empty meter reading zero.
     *
     * @param trailing nodes to place at the right end of the reading row, after a spacer;
     *                 typically the memory figures. May be empty.
     */
    RamMeter(javafx.scene.Node... trailing) {
        super(10);

        lblPercent.getStyleClass().add("meter-percent");
        lblCaption.getStyleClass().add("meter-caption");
        lblState.getStyleClass().add("meter-state");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox reading = new HBox(10, lblPercent, lblCaption, lblState, spacer);
        reading.setAlignment(Pos.BASELINE_LEFT);
        reading.getChildren().addAll(trailing);

        getChildren().addAll(reading, buildBar());
        setFillWidth(true);
    }

    /**
     * Points the meter at a new reading.
     *
     * @param usedPercent share of memory in use, from 0 to 100
     * @param state       the state to colour the bar with; must not be {@code null}
     */
    void update(double usedPercent, RamState state) {
        lblPercent.setText(usedPercent + "%");
        fraction.set(Math.max(0, Math.min(1, usedPercent / 100.0)));

        if (state != shownState) {
            shownState = state;
            applyStateStyle(state);
        }
    }

    /**
     * Builds the bar: a track filling the width, with the coloured fill laid over its left end.
     *
     * @return the assembled bar
     */
    private StackPane buildBar() {
        Region track = new Region();
        track.getStyleClass().add("meter-track");
        track.setMinHeight(BAR_HEIGHT);
        track.setPrefHeight(BAR_HEIGHT);
        track.setMaxHeight(BAR_HEIGHT);

        fill.getStyleClass().add("meter-fill");
        fill.setMinHeight(BAR_HEIGHT);
        fill.setPrefHeight(BAR_HEIGHT);
        fill.setMaxHeight(BAR_HEIGHT);

        StackPane bar = new StackPane(track, fill);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(BAR_HEIGHT);
        bar.setPrefHeight(BAR_HEIGHT);
        bar.setMaxHeight(BAR_HEIGHT);

        // The fill is sized rather than clipped, so it stays a rounded pill at any reading.
        fill.maxWidthProperty().bind(bar.widthProperty().multiply(fraction));
        fill.minWidthProperty().bind(fill.maxWidthProperty());
        return bar;
    }

    /**
     * Recolours the bar and the state pill for the given state.
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

        fill.getStyleClass().removeAll("stable", "warning", "critical");
        fill.getStyleClass().add(key);
        lblState.getStyleClass().removeAll("state-stable", "state-warning", "state-critical");
        lblState.getStyleClass().add("state-" + key);
        lblState.setText(state.name());
    }
}
