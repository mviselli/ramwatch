/**
 * User preferences: polling interval, RAM thresholds, process filter and logging.
 *
 * <p>{@link com.ramwatch.config.AppConfig} is the immutable snapshot of every setting, and
 * {@link com.ramwatch.config.ConfigStore} persists it locally and reloads it at startup,
 * falling back to the defaults when the stored file is missing or unreadable.
 */
package com.ramwatch.config;
