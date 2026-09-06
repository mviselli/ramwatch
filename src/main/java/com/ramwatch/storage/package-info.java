/**
 * Optional persistence of memory events: local text log and CSV export.
 *
 * <p>A {@link com.ramwatch.storage.MemoryEvent} is recorded when the RAM state changes.
 * {@link com.ramwatch.storage.EventLogger} appends it to the log file,
 * {@link com.ramwatch.storage.EventLogReader} reads it back and
 * {@link com.ramwatch.storage.EventCsvExporter} converts the recorded events to CSV.
 * Logging is always optional and can be disabled from the settings screen.
 */
package com.ramwatch.storage;
