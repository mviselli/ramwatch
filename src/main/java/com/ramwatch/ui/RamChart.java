package com.ramwatch.ui;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Area chart of recent memory usage, capped at a fixed number of samples.
 *
 * <p>Samples are held in a circular buffer: once it is full, adding one drops the oldest, so
 * the chart's memory footprint is bounded no matter how long the app runs. The vertical axis
 * is pinned to 0-100%, so the curve is comparable over time instead of rescaling on every
 * update, and animations and point symbols are off to keep redraws cheap.
 *
 * <p>Like every JavaFX node, it must only be touched on the application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class RamChart extends AreaChart<Number, Number> {

    /** Samples kept: five minutes at one sample per second. */
    private static final int MAX_SAMPLES = 300; // 5 minuti a 1 campione/sec

    /** The single plotted series, rebuilt from the buffer on every sample. */
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();

    /** Circular buffer of used-memory percentages, oldest first. */
    private final Deque<Double> buffer = new ArrayDeque<>(MAX_SAMPLES + 1);

    /** Number of samples received since startup. */
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
        setPrefHeight(160);

        getData().add(series);
    }

    /**
     * Appends one sample, dropping the oldest once the buffer is full.
     *
     * <p>The horizontal axis is rescaled to the samples held, so the curve fills the width
     * from the very first ones instead of starting squeezed on the left.
     *
     * @param usedPercent share of memory in use, from 0 to 100
     */
    public void addSample(double usedPercent) {
        buffer.addLast(usedPercent);
        if (buffer.size() > MAX_SAMPLES) {
            buffer.pollFirst();
        }

        series.getData().clear();
        int x = 0;
        for (double value : buffer) {
            series.getData().add(new XYChart.Data<>(x++, value));
        }

        NumberAxis xAxis = (NumberAxis) getXAxis();
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(Math.max(buffer.size() - 1, 1));

        tick++;
    }
}
