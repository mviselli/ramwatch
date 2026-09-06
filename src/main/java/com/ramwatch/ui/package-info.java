/**
 * JavaFX screens: dashboard, process table, history chart and settings.
 *
 * <p>{@link com.ramwatch.ui.DashboardController} receives each
 * {@link com.ramwatch.analysis.AnalyzedSnapshot} from the polling thread and hands it to
 * {@link com.ramwatch.ui.DashboardView} and {@link com.ramwatch.ui.RamChart} on the JavaFX
 * application thread. {@link com.ramwatch.ui.SettingsView} edits the user preferences and
 * applies them live.
 */
package com.ramwatch.ui;
