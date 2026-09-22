package com.livrovivo.app.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small, in-memory, single-flight cache. Speculation never writes a choice to the database. */
class ContinuationCache<K, V>(private val capacity: Int = 4) {
    private val mutex = Mutex()
    private val values = linkedMapOf<K, V>()
    private val pending = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun get(key: K, generate: suspend () -> V): V {
        var owner = false
        val deferred = mutex.withLock {
            values[key]?.let { return it }
            pending[key] ?: CompletableDeferred<V>().also { pending[key] = it; owner = true }
        }
        if (!owner) {
            try {
                return deferred.await()
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                // A background reader was closed, but this foreground request still needs the page.
                return get(key, generate)
            }
        }
        try {
            val value = generate()
            mutex.withLock {
                values[key] = value
                while (values.size > capacity) values.remove(values.keys.first())
                pending.remove(key)
            }
            deferred.complete(value)
            return value
        } catch (error: Throwable) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock { pending.remove(key) }
            }
            deferred.completeExceptionally(error)
            throw error
        }
    }
}
