package com.mi.onextbox.ui.common

import android.annotation.SuppressLint
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Spring-driven spotlight renderer for material surfaces. */
// High-API effects are guarded at creation and have a plain fallback.
@SuppressLint("NewApi")
internal class ColorOsSpotlightRenderer(
    private val host: View,
    style: Style,
    darkTheme: Boolean,
    private val invalidate: () -> Unit = { host.invalidate() },
) {
    enum class Style {
        /** COUISpotLightEffect.TYPE_TRANSLUCENT_LARGE_1. */
        Popup,

        /** COUISpotLightEffect.TYPE_OPAQUE_SMALL_2. */
        TopBarButton,

        /** COUISpotLightEffect.TYPE_OPAQUE_MEDIUM_2, used by SearchBarBackgroundView. */
        SearchBar,

        DialogPrimary,
        DialogSecondary,
    }

    private val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(ColorOs17SpotlightShader) }.getOrNull()
    } else {
        null
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val progressHolder = FloatValueHolder(0f)
    private val positionXHolder = FloatValueHolder(0.5f)
    private val positionYHolder = FloatValueHolder(0.5f)
    private val progressAnimation = SpringAnimation(progressHolder).apply {
        minimumVisibleChange = 0.0001f
        addUpdateListener { _, value, _ ->
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }
    }
    private val positionXAnimation = SpringAnimation(positionXHolder).apply {
        minimumVisibleChange = 0.0001f
        spring = followHandSpring(0.5f)
        addUpdateListener { _, value, _ ->
            positionX = value
            invalidate()
        }
    }
    private val positionYAnimation = SpringAnimation(positionYHolder).apply {
        minimumVisibleChange = 0.0001f
        spring = followHandSpring(0.5f)
        addUpdateListener { _, value, _ ->
            positionY = value
            invalidate()
        }
    }

    private var style = style
    private var darkTheme = darkTheme
    private var width = 0f
    private var height = 0f
    private var progress = 0f
    private var positionX = 0.5f
    private var positionY = 0.5f
    private var extinguishing = false

    fun updateAppearance(style: Style = this.style, darkTheme: Boolean = this.darkTheme) {
        if (this.style == style && this.darkTheme == darkTheme) return
        this.style = style
        this.darkTheme = darkTheme
        invalidate()
    }

    fun onSizeChanged(width: Int, height: Int) {
        this.width = width.toFloat()
        this.height = height.toFloat()
        shader?.setFloatUniform("resolution", this.width, this.height)
        invalidate()
    }

    fun onDown(x: Float, y: Float) {
        moveLightTo(x, y)
        extinguishing = false
        animateProgress(
            target = 1f,
            stiffness = GlowStiffness,
            dampingRatio = GlowDampingRatio,
        )
    }

    fun onMove(x: Float, y: Float) {
        moveLightTo(x, y)
    }

    fun onUp(x: Float, y: Float) {
        moveLightTo(x, y)
        extinguishing = true
        animateProgress(
            target = 0f,
            stiffness = ExtinguishStiffness,
            dampingRatio = ExtinguishDampingRatio,
        )
    }

    fun cancel() {
        progressAnimation.cancel()
        positionXAnimation.cancel()
        positionYAnimation.cancel()
        progress = 0f
        progressHolder.value = 0f
        extinguishing = false
        invalidate()
    }

    fun draw(canvas: Canvas) {
        val runtimeShader = shader ?: return
        if (!canvas.isHardwareAccelerated || width <= 0f || height <= 0f || progress <= 0f) {
            return
        }

        val params = parameters()
        val radius = params.startRadius + (params.endRadius - params.startRadius) * progress +
            if (extinguishing) params.endRadius * 2f * (1f - progress) else 0f
        val intensity = params.endIntensity * progress

        runtimeShader.setFloatUniform(
            "u_lightPosAndHeight",
            positionX * width,
            positionY * height,
            radius,
            intensity,
        )
        runtimeShader.setFloatUniform(
            "u_lightColorAndAmbient",
            1f,
            1f,
            1f,
            AmbientStrength,
        )
        runtimeShader.setFloatUniform(
            "u_lightingParams",
            DiffuseStrength,
            LightHeight,
            Noise,
            Dither,
        )

        paint.shader = runtimeShader
        paint.blendMode = params.blendMode
        canvas.drawPaint(paint)
        paint.shader = null
        paint.blendMode = BlendMode.SRC_OVER
    }

    private fun moveLightTo(x: Float, y: Float) {
        if (width <= 0f || height <= 0f) return
        val normalizedX = x / width
        val normalizedY = y / height
        if (progress == 0f) {
            positionXAnimation.cancel()
            positionYAnimation.cancel()
            positionX = normalizedX
            positionY = normalizedY
            positionXHolder.value = normalizedX
            positionYHolder.value = normalizedY
            invalidate()
        } else {
            positionXAnimation.animateToFinalPosition(normalizedX)
            positionYAnimation.animateToFinalPosition(normalizedY)
        }
    }

    private fun animateProgress(target: Float, stiffness: Float, dampingRatio: Float) {
        progressAnimation.spring = SpringForce(target).apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
        }
        progressAnimation.animateToFinalPosition(target)
    }

    private fun parameters(): Parameters = when (style) {
        Style.Popup -> Parameters(
            endIntensity = if (darkTheme) 0.1f else 0.6f,
            startRadius = 100f,
            endRadius = 800f,
            blendMode = BlendMode.OVERLAY,
        )

        Style.TopBarButton, Style.SearchBar -> Parameters(
            endIntensity = if (darkTheme) 0.15f else 1f,
            startRadius = 100f,
            endRadius = 500f,
            blendMode = if (darkTheme) BlendMode.PLUS else BlendMode.OVERLAY,
        )

        Style.DialogPrimary -> Parameters(
            endIntensity = if (darkTheme) 0.8f else 0.6f,
            startRadius = 100f,
            endRadius = 500f,
            blendMode = BlendMode.OVERLAY,
        )

        Style.DialogSecondary -> Parameters(
            endIntensity = if (darkTheme) 0.15f else 1f,
            startRadius = 100f,
            endRadius = 500f,
            blendMode = if (darkTheme) BlendMode.PLUS else BlendMode.OVERLAY,
        )
    }

    private data class Parameters(
        val endIntensity: Float,
        val startRadius: Float,
        val endRadius: Float,
        val blendMode: BlendMode,
    )

    private companion object {
        const val AmbientStrength = 0.4f
        const val DiffuseStrength = 0.4f
        const val LightHeight = 200f
        const val Noise = 0.08f
        const val Dither = 0f

        // COUISpringForce bounce/response pairs from ColorOS 17, converted to AndroidX
        // damping ratio and stiffness.
        const val GlowDampingRatio = 1f
        const val GlowStiffness = 1_754.5963f // bounce=0, response=.15
        const val ExtinguishDampingRatio = 1f
        const val ExtinguishStiffness = 246.74011f // bounce=0, response=.4
        const val FollowHandDampingRatio = 0.85f // bounce=.15 maps to 1 - bounce
        const val FollowHandStiffness = 322.2735f // response=.35

        fun followHandSpring(finalPosition: Float) = SpringForce(finalPosition).apply {
            stiffness = FollowHandStiffness
            dampingRatio = FollowHandDampingRatio
        }
    }
}

/** Exact AGSL from ColorOS 17 SpotLightShader. */
internal const val ColorOs17SpotlightShader = """
uniform vec4 u_lightPosAndHeight;
uniform vec4 u_lightColorAndAmbient;
uniform vec4 u_lightingParams;
uniform vec2 resolution;

float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float max3(vec3 v) {
    return max(max(v.r, v.g), v.b);
}

float noise(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float softLight(float A, float B) {
    float l = B - (1.0 - 2.0 * A) * B * (1.0 - B);
    float h = B + (2.0 * A - 1.0) * (sqrt(B) - B);
    return mix(l, h, step(0.5, A));
}

float dither(vec2 coord, float ditherN) {
    float n1 = fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
    float n2 = fract(sin(dot(coord + vec2(1.0), vec2(12.9898, 78.233))) * 43758.5453);
    return (n1 + n2 - 1.0) * ditherN / 255.0;
}

half4 main(float2 fragCoord) {
    float2 lightP = u_lightPosAndHeight.xy;
    float radius = u_lightPosAndHeight.z;
    float intensity = u_lightPosAndHeight.w;

    vec3 lightCol = u_lightColorAndAmbient.rgb;
    float ambientStr = u_lightColorAndAmbient.w;
    float diffuseStr = u_lightingParams.x;
    float lightH = u_lightingParams.y;
    float noise_exp = u_lightingParams.z;
    float ditherN = u_lightingParams.w;
    const float lightIntensity = 1.5;

    vec2 delta = fragCoord - lightP;
    vec3 L = vec3(delta, lightH);
    float dist = length(L);
    L = normalize(L);

    vec3 N = vec3(0.0, 0.0, 1.0);
    float diff = max(dot(N, L), 0.0);
    vec3 lighting = (ambientStr + diffuseStr * diff) * lightCol;
    if (dist >= radius) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float att = smoothstep(1.0, 0.0, dist / radius) * step(dist, radius);
    lighting *= att * lightIntensity * intensity;
    float alpha = clamp(luminance(lighting), 0.0, 1.0);

    float postOn = max(step(0.001, noise_exp), step(0.001, ditherN));
    vec3 postLighting = lighting;
    float postAlpha = alpha;

    float noiseOn = step(0.001, noise_exp);
    vec2 uv_pos = fragCoord * 9.259e-4;
    float n = clamp((noise(uv_pos) - 0.5) * noise_exp + 0.5, 0.0, 1.0);
    float mask = max3(postLighting);
    float fadeAlpha = clamp(softLight(n, mask), 0.0, 1.0);
    float noiseDelta = clamp(abs(fadeAlpha - mask), 0.0, 1.0);
    postLighting += vec3(noiseDelta) * noiseOn;

    float ditherOn = step(0.001, ditherN);
    float d = dither(fragCoord, ditherN);
    postLighting += vec3(d) * ditherOn;
    postAlpha = mix(postAlpha, clamp(postAlpha + d, 0.0, 1.0), ditherOn);

    lighting = mix(lighting, postLighting, postOn);
    alpha = mix(alpha, postAlpha, postOn);
    return half4(lighting, alpha);
}
"""
