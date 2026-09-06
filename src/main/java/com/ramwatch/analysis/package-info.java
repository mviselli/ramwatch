/**
 * Turns raw samples into the information the UI and the event log need.
 *
 * <p>Given a {@link com.ramwatch.system.SystemSnapshot}, this layer computes used and free
 * percentages, ranks the heaviest processes, applies the minimum-memory filter and derives
 * the current {@link com.ramwatch.analysis.RamState}. The result is an
 * {@link com.ramwatch.analysis.AnalyzedSnapshot}, so the UI never has to sort or classify
 * anything itself.
 */
package com.ramwatch.analysis;
