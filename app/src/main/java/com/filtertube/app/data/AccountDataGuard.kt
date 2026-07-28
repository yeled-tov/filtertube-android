package com.filtertube.app.data

/**
 * Coordinates the short local-storage portions of account migration, sign-out,
 * and cloud synchronization. Network calls never hold this lock.
 */
internal object AccountDataGuard {
    private val lock = Any()

    @Volatile
    private var generation: Long = 0L

    fun generation(): Long = generation

    fun invalidate() {
        synchronized(lock) {
            generation = if (generation == Long.MAX_VALUE) 0L else generation + 1L
        }
    }

    fun <T> withLock(block: () -> T): T = synchronized(lock, block)
}
