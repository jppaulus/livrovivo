package com.livrovivo.app.core.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ContinuationCacheTest {
    @Test fun `foreground shares an in flight preparation and reuses the result`() = runTest {
        val cache = ContinuationCache<String, String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val background = async { cache.get("path-a") { calls++; started.complete(Unit); release.await(); "page-a" } }
        started.await()
        val foreground = async { cache.get("path-a") { error("duplicate request") } }
        release.complete(Unit)
        assertEquals("page-a", foreground.await())
        assertEquals(background.await(), cache.get("path-a") { error("cache miss") })
        assertEquals(1, calls)
    }

    @Test fun `cancelled preparation can be generated again`() = runTest {
        val cache = ContinuationCache<String, String>()
        val started = CompletableDeferred<Unit>()
        val preparation = launch { cache.get("a") { started.complete(Unit); awaitCancellation() } }
        started.await()
        val foreground = async(start = CoroutineStart.UNDISPATCHED) { cache.get("a") { "recovered" } }
        preparation.cancelAndJoin()
        assertEquals("recovered", foreground.await())
    }

    @Test fun `failures are not cached and eviction stays bounded`() = runTest {
        val cache = ContinuationCache<String, String>(capacity = 2)
        try { cache.get("a") { error("unavailable") }; fail("expected failure") } catch (_: IllegalStateException) { }
        assertEquals("a", cache.get("a") { "a" })
        cache.get("b") { "b" }
        cache.get("c") { "c" }
        assertEquals("new-a", cache.get("a") { "new-a" })
        assertEquals("c", cache.get("c") { error("unexpected eviction") })
    }
}
