package com.filtertube.app.data

import java.util.concurrent.ConcurrentHashMap

/**
 * מנטר בריאות מנועי ניגון (Health & Cooldown Monitor).
 * אם מנוע ניגון (כגון ANDROID_VR או IOS) נכשל מספר פעמים ברצף,
 * הוא מוכנס ל-cooldown זמני כדי לא לעכב סרטונים הבאים.
 */
object ResolverHealthMonitor {

    private data class HealthState(
        var consecutiveFailures: Int = 0,
        var cooldownUntil: Long = 0L
    )

    private val states = ConcurrentHashMap<String, HealthState>()

    fun isAvailable(resolverId: String): Boolean {
        val state = states[resolverId] ?: return true
        val now = System.currentTimeMillis()
        if (now < state.cooldownUntil) {
            return false
        }
        // פג תוקף ה-cooldown — מאפסים ומאפשרים ניסיון נוסף
        if (state.cooldownUntil > 0L) {
            state.cooldownUntil = 0L
            state.consecutiveFailures = 0
            Diagnostics.log("HEALTH $resolverId: cooldown הסתיים, חוזר לפעילות")
        }
        return true
    }

    fun recordSuccess(resolverId: String) {
        val state = states.getOrPut(resolverId) { HealthState() }
        if (state.consecutiveFailures > 0 || state.cooldownUntil > 0L) {
            state.consecutiveFailures = 0
            state.cooldownUntil = 0L
            Diagnostics.log("HEALTH $resolverId: הצלחה ✓ (איפוס מפתח בריאות)")
        }
    }

    fun recordFailure(resolverId: String, reason: String) {
        val maxFailures = RemoteConfig.maxConsecutiveFailures(3)
        val cooldownMs = RemoteConfig.cooldownDurationMinutes(5) * 60 * 1000L

        val state = states.getOrPut(resolverId) { HealthState() }
        state.consecutiveFailures++

        if (state.consecutiveFailures >= maxFailures) {
            state.cooldownUntil = System.currentTimeMillis() + cooldownMs
            Diagnostics.log("HEALTH $resolverId: נכשל ${state.consecutiveFailures} פעמים ברצף ($reason) ← נכנס ל-cooldown ל-${cooldownMs / 60000} דק'")
        } else {
            Diagnostics.log("HEALTH $resolverId: כשלון ${state.consecutiveFailures}/$maxFailures ($reason)")
        }
    }

    fun clear() {
        states.clear()
    }
}
