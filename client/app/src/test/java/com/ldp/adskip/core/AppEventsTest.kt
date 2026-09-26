package com.ldp.adskip.core

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

/**
 * AppEvents JVM 单测：进程内状态桥（StateFlow 快照 + SharedFlow 投递）。
 */
class AppEventsTest {

    @Test
    fun `setServiceRunning updates state and snapshot`() {
        AppEvents.setServiceRunning(true)
        assertTrue(AppEvents.serviceRunning.value)
        assertTrue(AppEvents.serviceRunningSnapshot)

        AppEvents.setServiceRunning(false)
        assertFalse(AppEvents.serviceRunning.value)
        assertFalse(AppEvents.serviceRunningSnapshot)
    }

    @Test
    fun `snapshot mirrors state flow value`() {
        AppEvents.setServiceRunning(true)
        assertEquals(AppEvents.serviceRunning.value, AppEvents.serviceRunningSnapshot)
        AppEvents.setServiceRunning(false)
        assertEquals(AppEvents.serviceRunning.value, AppEvents.serviceRunningSnapshot)
    }

    @Test
    fun `emitSkipped delivers label to subscriber`() = runBlocking {
        // SharedFlow 无 replay：先让收集方完成订阅（注册 first()）再发射
        val deferred = async { withTimeout(2_000) { AppEvents.skipped.first() } }
        yield()
        AppEvents.emitSkipped("Example")
        assertEquals("Example", deferred.await())
    }
}