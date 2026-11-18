package com.mi.onextbox.lsp

import kotlin.math.PI
import java.util.WeakHashMap

internal object LauncherIosBounceRecovery {
    fun opensWindow(startArea: Double, previousTargetArea: Double, nextTargetArea: Double): Boolean =
        startArea.isFinite() && previousTargetArea.isFinite() && nextTargetArea.isFinite() &&
            startArea > 0.0 && previousTargetArea > 0.0 &&
            nextTargetArea >= startArea * .75 && nextTargetArea > previousTargetArea * 4.0

    fun velocity(position: Float, target: Float, inherited: Float, response: Double): Float {
        if (!position.isFinite() || !target.isFinite() || !inherited.isFinite() ||
            !response.isFinite() || response <= 0.0) return inherited
        val limit = ((target.toDouble() - position) * (2.0 * PI / response)).toFloat()
        if (!limit.isFinite()) return inherited
        return if (limit >= 0f) inherited.coerceIn(0f, limit) else inherited.coerceIn(limit, 0f)
    }
}

internal class LauncherIosBounceLifecycle<O : Any> {
    private class State(var route: LauncherIosMotionProfile.RouteKind? = null,
        var opening: Boolean = false, var superseded: Boolean = false, var seedFromBounce: Boolean = false,
        var confirmedHome: Boolean = false)
    private val states = WeakHashMap<O, State>()
    private var desktopVisible = false
    private var desktopResumed = false
    private var homeRequestGeneration = 0L

    @Synchronized fun homeRequestGeneration(): Long = homeRequestGeneration

    @Synchronized fun bind(owner: O, route: LauncherIosMotionProfile.RouteKind, homeRequest: Long? = null): List<O> {
        val state = states.getOrPut(owner) { State() }
        if (route == LauncherIosMotionProfile.RouteKind.SwipeToHome &&
            state.route == route && state.superseded) return emptyList()
        val home = route == LauncherIosMotionProfile.RouteKind.SwipeToHome
        if (home && homeRequest != null && homeRequest != homeRequestGeneration) {
            if (state.route == null) {
                state.route = route
                state.superseded = true
            }
            return emptyList()
        }
        state.route = route
        state.opening = route == LauncherIosMotionProfile.RouteKind.Opening
        state.confirmedHome = home && (homeRequest != null || state.confirmedHome)
        state.superseded = home && !desktopVisible && !state.confirmedHome
        if (!state.opening) return emptyList()
        return leaveDesktop()
    }

    @Synchronized fun leaveDesktop(): List<O> {
        desktopVisible = false
        desktopResumed = false
        return revokeHomeBounces()
    }

    @Synchronized fun revokeHomeBounces(): List<O> {
        homeRequestGeneration++
        val old = states.entries.filter { (_, value) ->
            value.route == LauncherIosMotionProfile.RouteKind.SwipeToHome && !value.superseded && !value.opening }.map { it.key }
        old.forEach { states[it]?.superseded = true }
        return old
    }

    @Synchronized fun startDesktop() { desktopVisible = true; desktopResumed = false }
    @Synchronized fun resumeDesktop(): List<O> {
        desktopVisible = true
        desktopResumed = true
        return states.entries.filter { (_, value) -> value.route == LauncherIosMotionProfile.RouteKind.SwipeToHome &&
            !value.superseded && !value.opening }.map { it.key }
    }

    @Synchronized fun open(owner: O, seedFromBounce: Boolean = false) {
        states.getOrPut(owner) { State() }.apply {
            opening = true
            confirmedHome = false
            this.seedFromBounce = this.seedFromBounce || seedFromBounce
        }
    }
    @Synchronized fun home(owner: O) {
        states.getOrPut(owner) { State() }.apply {
            superseded = superseded || route != LauncherIosMotionProfile.RouteKind.SwipeToHome || !desktopVisible
            route = LauncherIosMotionProfile.RouteKind.SwipeToHome
            opening = false
            confirmedHome = false
        }
    }
    @Synchronized fun takeBounceSeed(owner: O): Boolean {
        val state = states[owner] ?: return false
        val dirty = state.seedFromBounce
        state.seedFromBounce = false
        return dirty
    }
    @Synchronized fun wasHome(owner: O): Boolean = states[owner]?.route == LauncherIosMotionProfile.RouteKind.SwipeToHome
    @Synchronized fun opening(owner: O): Boolean? = states[owner]?.let {
        it.opening || (!it.superseded && it.route == LauncherIosMotionProfile.RouteKind.Opening)
    }
    @Synchronized fun superseded(owner: O): Boolean = states[owner]?.superseded == true
    @Synchronized fun route(owner: O): LauncherIosMotionProfile.RouteKind? = states[owner]?.let {
        if (it.opening) LauncherIosMotionProfile.RouteKind.Opening else it.route
    }
    @Synchronized fun allows(owner: O): Boolean = states[owner]?.let {
        it.route == LauncherIosMotionProfile.RouteKind.SwipeToHome && !it.opening && !it.superseded &&
            (it.confirmedHome || desktopVisible && desktopResumed)
    } == true
    @Synchronized fun clear() {
        states.clear(); desktopVisible = false; desktopResumed = false
        homeRequestGeneration++
    }
}
