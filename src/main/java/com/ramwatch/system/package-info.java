/**
 * Data models and OSHI integration: everything that reads the machine's state.
 *
 * <p>This is the source of the data flow. {@link com.ramwatch.system.SystemSampler}
 * queries OSHI once per polling cycle and returns an immutable
 * {@link com.ramwatch.system.SystemSnapshot} built from a
 * {@link com.ramwatch.system.MemorySnapshot} and a list of
 * {@link com.ramwatch.system.ProcessSnapshot}.
 * {@link com.ramwatch.system.PollingService} drives those cycles off the UI thread, and
 * {@link com.ramwatch.system.MemoryFormatter} turns raw byte counts into readable text.
 *
 * <p>The records in this package carry no UI or analysis logic: they only validate and
 * hold the values of a single sample.
 */
package com.ramwatch.system;
