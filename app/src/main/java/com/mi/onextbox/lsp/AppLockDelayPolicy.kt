package com.mi.onextbox.lsp

/** Owns deadlines only; it never creates an authentication grant. */
internal class AppLockDelayPolicy(private val capacity: Int = 256) {
    companion object { fun isVisibilityExit(reason: Any?) = reason == "notVisible" }
    data class Key(val user: Int, val pkg: String)
    data class Lease(val start: Long, val deadline: Long, val serial: Long) {
        val duration: Long get() = deadline - start
        fun expired(now: Long) = now < start || now >= deadline
        fun requiresRelock(now: Long, selectedDelay: Long) = expired(now) || selectedDelay != duration
    }
    private val leases = linkedMapOf<Key, Lease>()
    private var serial = 0L
    @Synchronized fun defer(key: Key, now: Long, delay: Long, authenticated: Boolean): Lease? {
        if (!authenticated || key.user < 0 || key.pkg.isBlank() || now < 0 || AppLockDelaySettings.valid(delay) == 0L) return null
        leases[key]?.let { return it.takeUnless { old -> old.requiresRelock(now, delay) } }
        if (leases.size >= capacity || now > Long.MAX_VALUE - delay) return null
        return Lease(now, now + delay, ++serial).also { leases[key] = it }
    }
    @Synchronized fun current(key: Key) = leases[key]
    @Synchronized fun hasPending() = leases.isNotEmpty()
    @Synchronized fun cancel(key: Key) = leases.remove(key)
    @Synchronized fun snapshot(): Map<Key, Lease> = leases.toMap()
    @Synchronized fun forget(user: Int, pkg: String?, allUsers: Boolean) {
        leases.keys.removeAll { allUsers || (it.user == user && (pkg == null || it.pkg == pkg)) }
    }
}
