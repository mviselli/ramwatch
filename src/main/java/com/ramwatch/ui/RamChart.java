package com.ramwatch.ui;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RamChart extends AreaChart<Number, Number> {

    private static final int MAX_SAMPLES = 300; // 5 minuti a 1 campione/sec

    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    private final Deque<Double> buffer = new ArrayDeque<>(MAX_SAMPLES + 1);
    private int tick = 0;

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
