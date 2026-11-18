package com.mi.onextbox.lsp

internal class LauncherIosSurfaceOwners<S : Any>(
    private val sameSurface: (S, S) -> Boolean,
) {
    class Lease(val generation: Long, var active: Boolean = true)

    private val owners = LinkedHashMap<S, Lease>()
    @Volatile private var occupied = false
    val isEmpty: Boolean get() = !occupied

    fun hasNewerOwner(surface: S, lease: Lease): Boolean =
        (findKey(surface)?.let(owners::get)?.generation ?: Long.MIN_VALUE) > lease.generation

    fun isOwned(surface: S, lease: Lease): Boolean = findKey(surface)?.let(owners::get) === lease

    fun canAcquire(surface: S, lease: Lease): Boolean {
        if (!lease.active) return false
        val current = findKey(surface)?.let(owners::get)
        return current == null || current.generation <= lease.generation
    }

    fun acquire(surface: S, lease: Lease): Boolean {
        if (!lease.active) return false
        val existingKey = findKey(surface)
        val current = if (existingKey != null) owners[existingKey] else null
        if (current != null && current.generation > lease.generation) {
            return false
        }
        if (existingKey != null && existingKey != surface) {
            owners.remove(existingKey)
        }
        owners[surface] = lease
        occupied = true
        return true
    }

    fun releaseIfOwned(surface: S, lease: Lease?): Boolean {
        val existingKey = findKey(surface) ?: return false
        val current = owners[existingKey]
        if (lease != null && current !== lease) return false
        owners.remove(existingKey)
        occupied = owners.isNotEmpty()
        return true
    }

    fun detachLease(lease: Lease): List<S> {
        lease.active = false
        val matched = owners.entries.filter { it.value === lease }.map { it.key }
        matched.forEach(owners::remove)
        occupied = owners.isNotEmpty()
        return matched
    }

    fun detachAll(): List<S> {
        val all = owners.keys.toList()
        owners.values.forEach { it.active = false }
        owners.clear()
        occupied = false
        return all
    }

    private fun findKey(surface: S): S? =
        owners.keys.firstOrNull { candidate -> candidate === surface || sameSurface(candidate, surface) }
}
