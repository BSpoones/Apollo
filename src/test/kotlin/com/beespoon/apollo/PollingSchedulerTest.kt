package com.beespoon.apollo
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import com.beespoon.apollo.reload.PollingScheduler
class PollingSchedulerTest {
    @Test fun firesReloadRepeatedly() {
        val latch = CountDownLatch(2)
        val scheduler = PollingScheduler(15) { latch.countDown() }
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        scheduler.close()
    }
    @Test fun survivesThrowingReload() {
        val runs = AtomicInteger()
        val scheduler = PollingScheduler(15) { runs.incrementAndGet(); throw RuntimeException("boom") }
        awaitUntil { runs.get() >= 2 }
        scheduler.close()
    }
    @Test fun closeStopsReloads() {
        val runs = AtomicInteger()
        val scheduler = PollingScheduler(15) { runs.incrementAndGet() }
        awaitUntil { runs.get() >= 1 }
        scheduler.close()
        val settled = runs.get()
        Thread.sleep(100)
        assertEquals(settled, runs.get())
    }
}
