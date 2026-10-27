package com.mi.onextbox.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.window.SplashScreen
import android.window.SplashScreenView
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.roundToInt

internal fun ComponentActivity.installAppLaunchAnimation(
    animate: Boolean,
    onExitStarted: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val animation = AppLaunchAnimation(this, animate, onExitStarted, onFinished)
    lifecycle.addObserver(animation)
    splashScreen.setOnExitAnimationListener(animation)
}

private class AppLaunchAnimation(
    private val activity: ComponentActivity,
    private val animate: Boolean,
    private val onExitStarted: () -> Unit,
    private val onFinished: () -> Unit,
) : SplashScreen.OnExitAnimationListener, DefaultLifecycleObserver {
    private var finished = false
    private var splashView: SplashScreenView? = null
    private var exitAnimator: ValueAnimator? = null
    private val exitAnimation = Runnable {
        val view = splashView ?: return@Runnable
        val background = view.background?.mutate()
        val children = (0 until view.childCount).map(view::getChildAt)
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 180L
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val alpha = it.animatedValue as Float
                background?.alpha = (255 * alpha).roundToInt()
                children.forEach { child -> child.alpha = alpha }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()
            })
        }
        exitAnimator = animator
        onExitStarted()
        animator.start()
    }

    override fun onSplashScreenExit(view: SplashScreenView) {
        if (finished) {
            view.remove()
            return
        }
        splashView = view
        if (!animate || !ValueAnimator.areAnimatorsEnabled() ||
            activity.isFinishing || activity.isDestroyed
        ) {
            finish()
            return
        }

        val duration = view.iconAnimationDuration?.toMillis() ?: 0L
        val startedAt = view.iconAnimationStart?.toEpochMilli()
        val remaining = if (startedAt != null) {
            (duration - (System.currentTimeMillis() - startedAt)).coerceIn(0L, duration)
        } else {
            0L
        }
        view.postDelayed(exitAnimation, remaining)
    }

    override fun onStop(owner: LifecycleOwner) = finish()

    override fun onDestroy(owner: LifecycleOwner) = finish()

    private fun finish() {
        if (finished) return
        finished = true
        activity.splashScreen.clearOnExitAnimationListener()
        activity.lifecycle.removeObserver(this)
        val view = splashView
        splashView = null
        view?.removeCallbacks(exitAnimation)
        exitAnimator?.apply {
            removeAllListeners()
            removeAllUpdateListeners()
            cancel()
        }
        exitAnimator = null
        view?.remove()
        onFinished()
    }
}
