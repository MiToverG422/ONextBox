package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import io.github.suqi8.coui.kmp.blur.RuntimeShader
import io.github.suqi8.coui.kmp.blur.asBrush
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val FeatureAuroraFrameIntervalMillis = 33L

/** Shader background for the Features page, with drawing stopped when detached. */
@Composable
internal fun FeaturePageAuroraBackground(
    isDarkTheme: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!remember { isRuntimeShaderSupported() }) {
        Spacer(modifier = modifier)
        return
    }

    val painter = remember { FeatureAuroraPainter() }
    val preset = remember(isDarkTheme) {
        if (isDarkTheme) FeatureAuroraPresets.Dark else FeatureAuroraPresets.Light
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .featureAuroraDraw(
                painter = painter,
                preset = preset,
                isDarkTheme = isDarkTheme,
                surfaceColor = surfaceColor,
            ),
    )
}

private class FeatureAuroraPreset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpolationPeriodSeconds: Float,
    val lightOffset: Float,
    val saturationOffset: Float,
    val pointOffset: Float,
)

private object FeatureAuroraPresets {
    val Light = FeatureAuroraPreset(
        points = floatArrayOf(
            0.8f, 0.2f, 1f,
            0.8f, 0.9f, 1f,
            0.2f, 0.9f, 1f,
            0.2f, 0.2f, 1f,
        ),
        colors1 = floatArrayOf(
            1f, 0.9f, 0.94f, 1f,
            1f, 0.84f, 0.89f, 1f,
            0.97f, 0.73f, 0.82f, 1f,
            0.64f, 0.65f, 0.98f, 1f,
        ),
        colors2 = floatArrayOf(
            0.58f, 0.74f, 1f, 1f,
            1f, 0.9f, 0.93f, 1f,
            0.74f, 0.76f, 1f, 1f,
            0.97f, 0.77f, 0.84f, 1f,
        ),
        colors3 = floatArrayOf(
            0.98f, 0.86f, 0.9f, 1f,
            0.6f, 0.73f, 0.98f, 1f,
            0.92f, 0.93f, 1f, 1f,
            0.56f, 0.69f, 1f, 1f,
        ),
        colorInterpolationPeriodSeconds = 5f,
        lightOffset = 0.1f,
        saturationOffset = 0.2f,
        pointOffset = 0.2f,
    )

    val Dark = FeatureAuroraPreset(
        points = Light.points,
        colors1 = floatArrayOf(
            0.2f, 0.06f, 0.88f, 0.4f,
            0.3f, 0.14f, 0.55f, 0.5f,
            0f, 0.64f, 0.96f, 0.5f,
            0.11f, 0.16f, 0.83f, 0.4f,
        ),
        colors2 = floatArrayOf(
            0.07f, 0.15f, 0.79f, 0.5f,
            0.62f, 0.21f, 0.67f, 0.5f,
            0.06f, 0.25f, 0.84f, 0.5f,
            0f, 0.2f, 0.78f, 0.5f,
        ),
        colors3 = floatArrayOf(
            0.58f, 0.3f, 0.74f, 0.4f,
            0.27f, 0.18f, 0.6f, 0.5f,
            0.66f, 0.26f, 0.62f, 0.5f,
            0.12f, 0.16f, 0.7f, 0.6f,
        ),
        colorInterpolationPeriodSeconds = 8f,
        lightOffset = 0f,
        saturationOffset = 0.17f,
        pointOffset = 0.4f,
    )
}

private fun Modifier.featureAuroraDraw(
    painter: FeatureAuroraPainter,
    preset: FeatureAuroraPreset,
    isDarkTheme: Boolean,
    surfaceColor: Color,
): Modifier = this then FeatureAuroraElement(
    painter = painter,
    preset = preset,
    isDarkTheme = isDarkTheme,
    surfaceColor = surfaceColor,
)

private data class FeatureAuroraElement(
    val painter: FeatureAuroraPainter,
    val preset: FeatureAuroraPreset,
    val isDarkTheme: Boolean,
    val surfaceColor: Color,
) : ModifierNodeElement<FeatureAuroraNode>() {
    override fun create(): FeatureAuroraNode = FeatureAuroraNode(
        painter = painter,
        preset = preset,
        isDarkTheme = isDarkTheme,
        surfaceColor = surfaceColor,
    )

    override fun update(node: FeatureAuroraNode) {
        node.update(
            painter = painter,
            preset = preset,
            isDarkTheme = isDarkTheme,
            surfaceColor = surfaceColor,
        )
    }
}

private class FeatureAuroraNode(
    private var painter: FeatureAuroraPainter,
    private var preset: FeatureAuroraPreset,
    private var isDarkTheme: Boolean,
    private var surfaceColor: Color,
) : Modifier.Node(), DrawModifierNode {
    private var animationJob: Job? = null
    private var animationTimeSeconds = 0f

    override fun onAttach() {
        startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: FeatureAuroraPainter,
        preset: FeatureAuroraPreset,
        isDarkTheme: Boolean,
        surfaceColor: Color,
    ) {
        this.painter = painter
        this.preset = preset
        this.isDarkTheme = isDarkTheme
        this.surfaceColor = surfaceColor
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        val startOffset = animationTimeSeconds
        animationJob = coroutineScope.launch {
            val origin = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                animationTimeSeconds = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
                delay(FeatureAuroraFrameIntervalMillis)
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surfaceColor)

        val effectHeight = size.height * 0.8f
        painter.updateResolution(size.width, size.height)
        painter.updateBounds(effectHeight, size.height, size.width)
        painter.updatePreset(preset, isDarkTheme)
        painter.updateColors(preset, preset.colorStageAt(animationTimeSeconds))
        painter.updateAnimation(animationTimeSeconds, preset)

        drawRect(painter.brush)
        drawContent()
    }
}

private fun FeatureAuroraPreset.colorStageAt(timeSeconds: Float): Float {
    val holdSeconds = colorInterpolationPeriodSeconds * 0.5f
    val transitionSeconds = 1.15f
    val segmentSeconds = holdSeconds + transitionSeconds
    val segment = floor(timeSeconds / segmentSeconds)
    val segmentTime = timeSeconds - segment * segmentSeconds
    val linearProgress = ((segmentTime - holdSeconds) / transitionSeconds).coerceIn(0f, 1f)
    // Smoothstep closely matches the old highly damped spring without a second frame clock.
    val easedProgress = linearProgress * linearProgress * (3f - 2f * linearProgress)
    return segment + easedProgress
}

private class FeatureAuroraPainter {
    private val runtimeShader = RuntimeShader(FeatureAuroraShader).apply {
        setFloatUniform("uTranslateY", 0f)
        setFloatUniform("uNoiseScale", 1.5f)
        setFloatUniform("uPointRadiusMulti", 1f)
        setFloatUniform("uAlphaMulti", 1f)
    }
    val brush: Brush = runtimeShader.asBrush()

    private val resolution = FloatArray(2)
    private val bounds = FloatArray(4)
    private val colors = FloatArray(16)
    private val animatedPoints = FloatArray(8)
    private var cachedPreset: FeatureAuroraPreset? = null
    private var cachedDarkTheme: Boolean? = null
    private var cachedColorStage = Float.NaN
    private var cachedWidth = Float.NaN
    private var cachedHeight = Float.NaN
    private var cachedEffectHeight = Float.NaN

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateBounds(effectHeight: Float, totalHeight: Float, totalWidth: Float) {
        if (
            cachedEffectHeight == effectHeight &&
            cachedHeight == totalHeight &&
            cachedWidth == totalWidth
        ) {
            return
        }

        val heightRatio = effectHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bounds[0] = 0f
            bounds[1] = 1f - heightRatio
            bounds[2] = 1f
            bounds[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bounds[0] = 0f
            bounds[1] = contentCenterY - aspectRatio / 2f
            bounds[2] = 1f
            bounds[3] = aspectRatio
        }
        runtimeShader.setFloatUniform("uBound", bounds)

        cachedEffectHeight = effectHeight
        cachedHeight = totalHeight
        cachedWidth = totalWidth
    }

    fun updatePreset(preset: FeatureAuroraPreset, isDarkTheme: Boolean) {
        if (cachedPreset === preset && cachedDarkTheme == isDarkTheme) return

        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturationOffset)
        cachedPreset = preset
        cachedDarkTheme = isDarkTheme
        cachedColorStage = Float.NaN
    }

    fun updateColors(preset: FeatureAuroraPreset, stage: Float) {
        if (cachedPreset === preset && cachedColorStage == stage) return

        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycleIndex(preset, base)
        val end = colorsForCycleIndex(preset, base + 1)
        for (index in colors.indices) {
            colors[index] = start[index] + (end[index] - start[index]) * fraction
        }
        runtimeShader.setFloatUniform("uColors", colors)
        cachedColorStage = stage
    }

    fun updateAnimation(time: Float, preset: FeatureAuroraPreset) {
        runtimeShader.setFloatUniform("uAnimTime", time)
        for (index in 0 until 4) {
            val sourceX = preset.points[index * 3]
            val sourceY = preset.points[index * 3 + 1]
            val animatedX = sourceX + sin(time + sourceY) * preset.pointOffset
            val animatedY = sourceY + cos(time + animatedX) * preset.pointOffset
            animatedPoints[index * 2] = animatedX
            animatedPoints[index * 2 + 1] = animatedY
        }
        runtimeShader.setFloatUniform("uPointsAnim", animatedPoints)
    }

    private fun colorsForCycleIndex(
        preset: FeatureAuroraPreset,
        index: Int,
    ): FloatArray = when (index.mod(4)) {
        1 -> preset.colors1
        3 -> preset.colors3
        else -> preset.colors2
    }
}

private const val FeatureAuroraShader = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) +
            (d - b) * u.x * u.y;
    }

    float gradientNoise(vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0.0, uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime));
        for (int i = 0; i < 4; i++) {
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float radius = uPoints[i].z * uPointRadiusMulti;
            float distanceToPoint = distance(uv, point);
            float amount = smoothstep(radius, 0.0, distanceToPoint);
            color.rgb = mix(color.rgb, pointColor.rgb, amount);
            color.a = mix(color.a, pointColor.a, amount);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= max(color.a, 0.0001);
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti;
        color += (10.0 / 255.0) * gradientNoise(fragCoord) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
