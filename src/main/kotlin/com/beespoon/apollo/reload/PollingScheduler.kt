package com.beespoon.apollo.reload

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class PollingScheduler(intervalMs: Long, reload: () -> Unit) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Apollo-Poller").apply {
            isDaemon = true
        }
    }

    init {
        executor.scheduleWithFixedDelay({ runCatching { reload() } }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
