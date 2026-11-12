package com.mi.onextbox.lsp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Surface
import kotlin.math.abs
import kotlin.math.exp

/** The rotation vector fuses gyro and gravity. Project the initial screen normal onto
 * the current screen axes so the light also responds when the phone starts upright. */
internal class SoftLightTilt(
    private val main: Handler,
    private val context: () -> Context?,
    private val visible: () -> Boolean,
    private val moved: (Float, Float) -> Unit,
) : SensorEventListener {
    @Volatile private var listening = false
    @Volatile private var unavailable = false
    private var manager: SensorManager? = null
    private var display: Display? = null
    private var power: PowerManager? = null
    private val matrix = FloatArray(9)
    private val reference = FloatArray(3)
    private var lastSample = 0L
    private var sentX = 0f
    private var sentY = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private val check = object : Runnable {
        override fun run() {
            if (power?.isInteractive != true || !visible()) stop()
            else main.postDelayed(this, 1000)
        }
    }

    fun request() {
        if (listening || unavailable || Looper.myLooper() != main.looper) return
        val host = context() ?: return
        power = host.getSystemService(PowerManager::class.java)
        if (power?.isInteractive != true) return
        val sensors = host.getSystemService(SensorManager::class.java) ?: return
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) { unavailable = true; return }
        display = host.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        manager = sensors
        listening = sensors.registerListener(this, sensor, 50_000, main)
        if (listening) main.postDelayed(check, 1000)
    }

    fun stop() {
        main.removeCallbacks(check)
        manager?.unregisterListener(this)
        listening = false
        lastSample = 0L
        smoothX = 0f
        smoothY = 0f
        if (sentX != 0f || sentY != 0f) {
            sentX = 0f
            sentY = 0f
            moved(0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!listening) return
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        if (lastSample == 0L) {
            for (i in 0..2) reference[i] = matrix[3*i+2]
            lastSample = event.timestamp
            return
        }
        val horizontal = reference[0]*matrix[0] + reference[1]*matrix[3] + reference[2]*matrix[6]
        val vertical = reference[0]*matrix[1] + reference[1]*matrix[4] + reference[2]*matrix[7]
        val rotation = display?.rotation
        val x = when (rotation) {
            Surface.ROTATION_90 -> vertical
            Surface.ROTATION_180 -> -horizontal
            Surface.ROTATION_270 -> -vertical
            else -> horizontal
        }
        val y = when (rotation) {
            Surface.ROTATION_90 -> horizontal
            Surface.ROTATION_180 -> vertical
            Surface.ROTATION_270 -> -horizontal
            else -> -vertical
        }
        val blend = (1.0 - exp(-(event.timestamp-lastSample).coerceAtLeast(0) / 110_000_000.0)).toFloat()
        lastSample = event.timestamp
        smoothX += (x-smoothX)*blend
        smoothY += (y-smoothY)*blend
        if (abs(smoothX-sentX) + abs(smoothY-sentY) < .014f) return
        sentX = smoothX
        sentY = smoothY
        moved(sentX, sentY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
