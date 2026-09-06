package com.ramwatch.ui;

import javafx.collections.ObservableList;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

/**
 * Area chart of recent memory usage, capped at a fixed number of samples.
 *
 * <p>The plotted series <em>is</em> the circular buffer: a sample is appended and, once the
 * cap is reached, the oldest point is dropped. Nothing else holds the history, so the chart
 * costs one point per cycle and its memory footprint is bounded no matter how long the app
 * runs. The history holds percentages only, never process lists or whole snapshots.
 *
 * <p>Points keep the absolute sample number as their x value and the horizontal axis slides
 * over them, instead of the series being renumbered from zero on every update: that is what
 * lets a cycle append one point rather than rebuild all {@value #MAX_SAMPLES}.
 *
 * <p>The vertical axis is pinned to 0-100%, so the curve is comparable over time instead of
 * rescaling on every update, and animations and point symbols are off to keep redraws cheap.
 *
 * <p>Like every JavaFX node, it must only be touched on the application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class RamChart extends AreaChart<Number, Number> {

    /** Samples kept: five minutes at one sample per second. */
    private static final int MAX_SAMPLES = 300; // 5 minuti a 1 campione/sec

    /** The single plotted series, which doubles as the circular buffer of samples. */
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();

    /** Number of samples received since startup, used as the x value of the next point. */
    private int tick = 0;

    /**
     * Creates an empty chart with fixed axes, no legend and no animation.
     */
    public RamChart() {
        super(new NumberAxis(), new NumberAxis());

        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);

        NumberAxis yAxis = (NumberAxis) getYAxis();
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(100);
        yAxis.setTickUnit(25);
        yAxis.setLabel("%");

        setLegendVisible(false);
        setAnimated(false);
        setCreateSymbols(false);
        setTitle(null);
        setPrefHeight(132);

        getData().add(series);
    }

    /**
     * Appends one sample, dropping the oldest once the buffer is full.
     *
     * <p>The horizontal axis slides to cover exactly the samples held, so the curve fills the
     * width from the very first ones instead of starting squeezed on the left.
     *
     * @param usedPercent share of memory in use, from 0 to 100
     */
    public void addSample(double usedPercent) {
        ObservableList<XYChart.Data<Number, Number>> points = series.getData();

        points.add(new XYChart.Data<>(tick, usedPercent));
        if (points.size() > MAX_SAMPLES) {
            points.remove(0);
        }

        int oldest = points.get(0).getXValue().intValue();
        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setLowerBound(oldest);
        xAxis.setUpperBound(Math.max(tick, oldest + 1));

        tick++;
    }
}
