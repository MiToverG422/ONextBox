package com.mi.onextbox.ui.onboarding

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Selects which half of an OPlus side-by-side colour/matte movie is shown.
 *
 * [PremultipliedForeground] reproduces BootReg's important compositing rule: the left half is RGB,
 * the red channel of the right half is alpha, nearly opaque pixels are forced to alpha 1, and all
 * other RGB values are premultiplied before they reach Android's Surface compositor.
 */
internal enum class BootregMatteOutput {
    PremultipliedForeground,
    OpaqueColor,
    AlphaMask,
}

/**
 * Plays one of BootReg's RGB+matte videos through a GLES 3 renderer.
 *
 * The decoder writes into an external-OES [SurfaceTexture]. GLES samples the two horizontal halves
 * in the same frame and renders into a non-opaque [TextureView]. Using TextureView rather than
 * GLSurfaceView keeps this surface in normal Android/Compose ordering, so regular Compose content
 * can be placed both before and after this composable without SurfaceView z-order surprises.
 *
 * This is intentionally separate from [BootregVideoPlayer], which is for ordinary full-frame MP4s.
 */
@Composable
internal fun BootregMatteVideoPlayer(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier,
    scaleMode: BootregVideoScaleMode = BootregVideoScaleMode.Crop,
    output: BootregMatteOutput = BootregMatteOutput.PremultipliedForeground,
    playCount: Int = 1,
    autoPlay: Boolean = true,
    onFirstFrame: () -> Unit = {},
    onCompleted: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    require(playCount > 0) { "playCount must be positive" }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentCompleted by rememberUpdatedState(onCompleted)
    val currentError by rememberUpdatedState(onError)
    val currentAutoPlay by rememberUpdatedState(autoPlay)
    // A Compose host can replace its LifecycleOwner while retaining the same Context. Include the
    // owner in the key so an old, permanently released TextureView is never reattached.
    val view = remember(context, videoResId, playCount, lifecycleOwner) {
        BootregMatteTextureView(
            context = context,
            videoResId = videoResId,
            playCount = playCount,
        )
    }

    DisposableEffect(view, lifecycleOwner) {
        view.updateCallbacks(
            onFirstFrame = { currentFirstFrame() },
            onCompleted = { currentCompleted() },
            onError = { currentError(it) },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.setPlaybackAllowed(currentAutoPlay)
                Lifecycle.Event.ON_STOP -> view.setPlaybackAllowed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        view.setPlaybackAllowed(
            autoPlay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.release()
        }
    }
    SideEffect {
        view.updateCallbacks(
            onFirstFrame = { currentFirstFrame() },
            onCompleted = { currentCompleted() },
            onError = { currentError(it) },
        )
        view.setPlaybackAllowed(
            autoPlay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    AndroidView(
        factory = { view },
        modifier = modifier,
        update = {
            it.setScaleMode(scaleMode)
            it.setOutput(output)
        },
    )
}

/**
 * C17's native depth-video composition: one decoder, an opaque normal layer below [content], and
 * the premultiplied matte layer above it. Both TextureViews are rendered from the same
 * `updateTexImage()` call, so the layers cannot drift apart as two MediaPlayers can.
 *
 * [fadeInDurationMillis] should be 600 for GuidePage's little-cloth sequence and 0 for the
 * CompletePage sequence. The player keeps the final decoded frame until this composable leaves.
 */
@Composable
internal fun BootregMatteVideoSandwich(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier,
    scaleMode: BootregVideoScaleMode = BootregVideoScaleMode.Crop,
    playCount: Int = 1,
    autoPlay: Boolean = true,
    layersVisible: Boolean = true,
    fadeInDurationMillis: Int = 0,
    onFirstFrame: () -> Unit = {},
    onCompleted: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    require(playCount > 0) { "playCount must be positive" }
    require(fadeInDurationMillis >= 0) { "fadeInDurationMillis must not be negative" }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentCompleted by rememberUpdatedState(onCompleted)
    val currentError by rememberUpdatedState(onError)
    val currentAutoPlay by rememberUpdatedState(autoPlay)
    var layersReady by remember(context, videoResId, playCount, lifecycleOwner) {
        mutableStateOf(false)
    }
    val controller = remember(context, videoResId, playCount, lifecycleOwner) {
        BootregMatteSceneController(
            context = context,
            videoResId = videoResId,
            playCount = playCount,
        )
    }
    val layerAlpha by animateFloatAsState(
        targetValue = if (layersReady && layersVisible) 1f else 0f,
        animationSpec = tween(durationMillis = fadeInDurationMillis),
        label = "bootregMatteLayersAlpha",
    )

    DisposableEffect(controller, lifecycleOwner) {
        controller.updateCallbacks(
            onFirstFrame = {
                layersReady = true
                currentFirstFrame()
            },
            onCompleted = { currentCompleted() },
            onError = { currentError(it) },
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.setPlaybackAllowed(currentAutoPlay)
                Lifecycle.Event.ON_STOP -> controller.setPlaybackAllowed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        controller.setPlaybackAllowed(
            autoPlay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    SideEffect {
        controller.updateCallbacks(
            onFirstFrame = {
                layersReady = true
                currentFirstFrame()
            },
            onCompleted = { currentCompleted() },
            onError = { currentError(it) },
        )
        controller.setScaleMode(scaleMode)
        controller.setPlaybackAllowed(
            autoPlay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { controller.normalView },
            modifier = Modifier
                .fillMaxSize()
                .alpha(layerAlpha),
        )
        content()
        AndroidView(
            factory = { controller.foregroundView },
            modifier = Modifier
                .fillMaxSize()
                .alpha(layerAlpha),
        )
    }
}

private class BootregMatteOutputView(
    context: Context,
    opaque: Boolean,
    private val onAvailable: (SurfaceTexture) -> Unit,
    private val onDestroyed: (SurfaceTexture) -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener, View.OnLayoutChangeListener {
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var scaleMode = BootregVideoScaleMode.Crop

    init {
        isOpaque = opaque
        alpha = 0f
        surfaceTextureListener = this
        addOnLayoutChangeListener(this)
    }

    fun setVideoGeometry(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        updateTransform()
    }

    fun setScaleMode(value: BootregVideoScaleMode) {
        if (scaleMode == value) return
        scaleMode = value
        updateTransform()
    }

    private fun updateTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) return
        val transform = Matrix()
        if (scaleMode != BootregVideoScaleMode.Stretch) {
            val widthRatio = viewWidth / sourceWidth.toFloat()
            val heightRatio = viewHeight / sourceHeight.toFloat()
            val uniformRatio = when (scaleMode) {
                BootregVideoScaleMode.Fit -> min(widthRatio, heightRatio)
                BootregVideoScaleMode.Crop -> max(widthRatio, heightRatio)
                BootregVideoScaleMode.Stretch -> error("Handled above")
            }
            transform.setScale(
                uniformRatio / widthRatio,
                uniformRatio / heightRatio,
                viewWidth / 2f,
                viewHeight / 2f,
            )
        }
        setTransform(transform)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        onAvailable(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        updateTransform()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        alpha = 0f
        onDestroyed(surface)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        updateTransform()
    }

    fun releaseListener() {
        removeOnLayoutChangeListener(this)
        if (surfaceTextureListener === this) surfaceTextureListener = null
    }
}

private class BootregMatteSceneController(
    context: Context,
    @RawRes private val videoResId: Int,
    private val playCount: Int,
) {
    private val appContext = context.applicationContext
    private var normalSurfaceTexture: SurfaceTexture? = null
    private var foregroundSurfaceTexture: SurfaceTexture? = null
    private var renderer: BootregMatteRenderer? = null
    private var player: MediaPlayer? = null
    private var decoderSurface: Surface? = null
    private var prepared = false
    private var playbackAllowed = false
    private var completed = false
    private var completedPlayCount = 0
    private var scaleMode = BootregVideoScaleMode.Crop
    private var pipelineReleasePending = false
    private var onFirstFrame: () -> Unit = {}
    private var onCompleted: () -> Unit = {}
    private var onError: (Throwable) -> Unit = {}
    private var released = false

    val normalView = BootregMatteOutputView(
        context = context,
        opaque = true,
        onAvailable = {
            normalSurfaceTexture = it
            maybeStartPipeline()
        },
        onDestroyed = {
            if (normalSurfaceTexture === it) normalSurfaceTexture = null
            releasePipeline()
        },
    )
    val foregroundView = BootregMatteOutputView(
        context = context,
        opaque = false,
        onAvailable = {
            foregroundSurfaceTexture = it
            maybeStartPipeline()
        },
        onDestroyed = {
            if (foregroundSurfaceTexture === it) foregroundSurfaceTexture = null
            releasePipeline()
        },
    )

    fun updateCallbacks(
        onFirstFrame: () -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        this.onFirstFrame = onFirstFrame
        this.onCompleted = onCompleted
        this.onError = onError
    }

    fun setScaleMode(value: BootregVideoScaleMode) {
        scaleMode = value
        normalView.setScaleMode(value)
        foregroundView.setScaleMode(value)
    }

    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        val currentPlayer = player ?: return
        if (!prepared || completed) return
        runCatching {
            if (allowed) currentPlayer.start()
            else if (currentPlayer.isPlaying) currentPlayer.pause()
        }.onFailure(::reportError)
    }

    private fun maybeStartPipeline() {
        if (released || renderer != null || pipelineReleasePending) return
        val normal = normalSurfaceTexture ?: return
        val foreground = foregroundSurfaceTexture ?: return
        val newRenderer = BootregMatteRenderer(
            outputTargets = listOf(
                BootregMatteTarget(normal, BootregMatteOutput.OpaqueColor),
                BootregMatteTarget(foreground, BootregMatteOutput.PremultipliedForeground),
            ),
            output = BootregMatteOutput.PremultipliedForeground,
            onDecoderSurfaceReady = ::openPlayer,
            onFirstFrame = {
                // Publish both layers together only after both eglSwapBuffers calls succeeded.
                normalView.alpha = 1f
                foregroundView.alpha = 1f
                onFirstFrame()
            },
            onError = {
                renderer = null
                releasePlayer()
                reportError(it)
            },
        )
        renderer = newRenderer
        newRenderer.start()
    }

    private fun openPlayer(surface: Surface) {
        if (released || player != null || renderer == null) {
            surface.release()
            return
        }
        decoderSurface = surface
        val newPlayer = MediaPlayer()
        player = newPlayer
        try {
            appContext.resources.openRawResourceFd(videoResId).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            newPlayer.setSurface(surface)
            newPlayer.setVolume(0f, 0f)
            newPlayer.isLooping = playCount == Int.MAX_VALUE
            newPlayer.setOnPreparedListener {
                prepared = true
                if (playbackAllowed) it.start()
            }
            newPlayer.setOnVideoSizeChangedListener { _, width, height ->
                val layerWidth = width / 2
                normalView.setVideoGeometry(layerWidth, height)
                foregroundView.setVideoGeometry(layerWidth, height)
            }
            newPlayer.setOnCompletionListener {
                completedPlayCount++
                if (completedPlayCount < playCount) {
                    it.seekTo(0, MediaPlayer.SEEK_CLOSEST)
                    if (playbackAllowed) it.start()
                } else {
                    completed = true
                    onCompleted()
                }
            }
            newPlayer.setOnErrorListener { _, what, extra ->
                reportError(IllegalStateException("MediaPlayer error what=$what extra=$extra"))
                true
            }
            newPlayer.prepareAsync()
        } catch (throwable: Throwable) {
            releasePlayer()
            reportError(throwable)
        }
    }

    private fun reportError(throwable: Throwable) {
        if (!released) onError(throwable)
    }

    private fun releasePipeline() {
        releasePlayer()
        val oldRenderer = renderer
        renderer = null
        if (oldRenderer != null) {
            pipelineReleasePending = true
            oldRenderer.release {
                pipelineReleasePending = false
                if (!released) maybeStartPipeline()
            }
        }
        normalView.alpha = 0f
        foregroundView.alpha = 0f
    }

    private fun releasePlayer() {
        prepared = false
        player?.runCatching {
            setSurface(null)
            reset()
            release()
        }
        player = null
        decoderSurface?.release()
        decoderSurface = null
    }

    fun release() {
        if (released) return
        released = true
        normalView.releaseListener()
        foregroundView.releaseListener()
        releasePipeline()
    }
}

private class BootregMatteTextureView(
    context: Context,
    @RawRes private val videoResId: Int,
    private val playCount: Int,
) : TextureView(context), TextureView.SurfaceTextureListener, View.OnLayoutChangeListener {
    private val appContext = context.applicationContext
    private var renderer: BootregMatteRenderer? = null
    private var player: MediaPlayer? = null
    private var decoderSurface: Surface? = null
    private var prepared = false
    private var playbackAllowed = false
    private var completed = false
    private var completedPlayCount = 0
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var scaleMode = BootregVideoScaleMode.Fit
    private var output = BootregMatteOutput.PremultipliedForeground
    private var onFirstFrame: () -> Unit = {}
    private var onCompleted: () -> Unit = {}
    private var onError: (Throwable) -> Unit = {}
    private var released = false

    init {
        isOpaque = false
        alpha = 0f
        surfaceTextureListener = this
        addOnLayoutChangeListener(this)
        surfaceTexture?.takeIf { isAvailable }?.let(::startPipeline)
    }

    fun updateCallbacks(
        onFirstFrame: () -> Unit,
        onCompleted: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        this.onFirstFrame = onFirstFrame
        this.onCompleted = onCompleted
        this.onError = onError
    }

    fun setScaleMode(value: BootregVideoScaleMode) {
        if (scaleMode == value) return
        scaleMode = value
        updateTransform()
    }

    fun setOutput(value: BootregMatteOutput) {
        output = value
        renderer?.setOutput(value)
    }

    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        val currentPlayer = player ?: return
        if (!prepared || completed) return
        runCatching {
            if (allowed) {
                currentPlayer.start()
            } else if (currentPlayer.isPlaying) {
                currentPlayer.pause()
            }
        }.onFailure(::reportError)
    }

    private fun startPipeline(outputSurfaceTexture: SurfaceTexture) {
        if (released || renderer != null) return
        val newRenderer = BootregMatteRenderer(
            outputTargets = listOf(
                BootregMatteTarget(outputSurfaceTexture, output),
            ),
            output = output,
            onDecoderSurfaceReady = ::openPlayer,
            onFirstFrame = {
                alpha = 1f
                onFirstFrame()
            },
            onError = {
                releasePlayer()
                reportError(it)
            },
        )
        renderer = newRenderer
        newRenderer.start()
    }

    private fun openPlayer(surface: Surface) {
        if (released || player != null || renderer == null) {
            surface.release()
            return
        }
        decoderSurface = surface
        val newPlayer = MediaPlayer()
        player = newPlayer
        try {
            appContext.resources.openRawResourceFd(videoResId).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            newPlayer.setSurface(surface)
            newPlayer.setVolume(0f, 0f)
            newPlayer.isLooping = playCount == Int.MAX_VALUE
            newPlayer.setOnPreparedListener {
                prepared = true
                if (playbackAllowed) it.start()
            }
            newPlayer.setOnVideoSizeChangedListener { _, width, height ->
                sourceWidth = width / 2
                sourceHeight = height
                updateTransform()
            }
            newPlayer.setOnCompletionListener {
                completedPlayCount++
                if (completedPlayCount < playCount) {
                    it.seekTo(0, MediaPlayer.SEEK_CLOSEST)
                    if (playbackAllowed) it.start()
                } else {
                    completed = true
                    onCompleted()
                }
            }
            newPlayer.setOnErrorListener { _, what, extra ->
                reportError(IllegalStateException("MediaPlayer error what=$what extra=$extra"))
                true
            }
            newPlayer.prepareAsync()
        } catch (throwable: Throwable) {
            reportError(throwable)
            releasePlayer()
        }
    }

    private fun updateTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) return

        val transform = Matrix()
        if (scaleMode != BootregVideoScaleMode.Stretch) {
            val widthRatio = viewWidth / sourceWidth.toFloat()
            val heightRatio = viewHeight / sourceHeight.toFloat()
            val uniformRatio = when (scaleMode) {
                BootregVideoScaleMode.Fit -> min(widthRatio, heightRatio)
                BootregVideoScaleMode.Crop -> max(widthRatio, heightRatio)
                BootregVideoScaleMode.Stretch -> error("Handled above")
            }
            transform.setScale(
                uniformRatio / widthRatio,
                uniformRatio / heightRatio,
                viewWidth / 2f,
                viewHeight / 2f,
            )
        }
        setTransform(transform)
    }

    private fun reportError(throwable: Throwable) {
        post { if (!released) onError(throwable) }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startPipeline(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        updateTransform()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePipeline()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        updateTransform()
    }

    fun release() {
        if (released) return
        released = true
        removeOnLayoutChangeListener(this)
        if (surfaceTextureListener === this) surfaceTextureListener = null
        releasePipeline()
    }

    private fun releasePipeline() {
        releasePlayer()
        renderer?.release()
        renderer = null
        alpha = 0f
    }

    private fun releasePlayer() {
        prepared = false
        player?.runCatching {
            setSurface(null)
            reset()
            release()
        }
        player = null
        decoderSurface?.release()
        decoderSurface = null
    }
}

private data class BootregMatteTarget(
    val surfaceTexture: SurfaceTexture,
    val output: BootregMatteOutput,
)

private data class BootregMatteEglTarget(
    val descriptor: BootregMatteTarget,
    val window: Surface,
    val eglSurface: EGLSurface,
)

private class BootregMatteRenderer(
    private val outputTargets: List<BootregMatteTarget>,
    output: BootregMatteOutput,
    private val onDecoderSurfaceReady: (Surface) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderThread = HandlerThread("BootregMatteRenderer")
    private lateinit var renderHandler: Handler
    private val released = AtomicBoolean(false)
    private val cleanupComplete = AtomicBoolean(false)
    private val releaseCallbacks = ConcurrentLinkedQueue<() -> Unit>()
    private var output = output
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private val eglTargets = mutableListOf<BootregMatteEglTarget>()
    private var decoderTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private var externalTextureId = 0
    private var vertexArrayId = 0
    private var vertexBufferId = 0
    private var program = 0
    private var textureLocation = -1
    private var textureMatrixLocation = -1
    private var outputModeLocation = -1
    private var firstFrameRendered = false
    private val textureMatrix = FloatArray(16)
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(VERTICES.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(VERTICES)
            position(0)
        }

    fun start() {
        renderThread.start()
        renderHandler = Handler(renderThread.looper)
        renderHandler.post {
            runCatching(::initialize)
                .onFailure(::fail)
        }
    }

    fun setOutput(value: BootregMatteOutput) {
        output = value
        if (::renderHandler.isInitialized) {
            renderHandler.post { if (!released.get()) renderFrame(updateImage = false) }
        }
    }

    private fun initialize() {
        check(!released.get()) { "Renderer released before initialization" }
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to obtain EGL display" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
            "eglInitialize failed: 0x${EGL14.eglGetError().toString(16)}"
        }

        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        val configAttributes = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        check(
            EGL14.eglChooseConfig(
                display,
                configAttributes,
                0,
                configs,
                0,
                1,
                configCount,
                0,
            ) && configCount[0] > 0,
        ) { "No RGBA8888 GLES3 window config" }
        val config = checkNotNull(configs[0])
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE),
            0,
        )
        check(context != EGL14.EGL_NO_CONTEXT) {
            "eglCreateContext failed: 0x${EGL14.eglGetError().toString(16)}"
        }

        check(outputTargets.isNotEmpty()) { "At least one matte-video output is required" }
        outputTargets.forEach { target ->
            val outputWindow = Surface(target.surfaceTexture)
            val outputSurface = EGL14.eglCreateWindowSurface(
                display,
                config,
                outputWindow,
                intArrayOf(EGL14.EGL_NONE),
                0,
            )
            if (outputSurface == EGL14.EGL_NO_SURFACE) {
                outputWindow.release()
                error("eglCreateWindowSurface failed: 0x${EGL14.eglGetError().toString(16)}")
            }
            eglTargets += BootregMatteEglTarget(target, outputWindow, outputSurface)
        }
        val firstTarget = eglTargets.first()
        check(
            EGL14.eglMakeCurrent(
                display,
                firstTarget.eglSurface,
                firstTarget.eglSurface,
                context,
            ),
        ) {
            "eglMakeCurrent failed: 0x${EGL14.eglGetError().toString(16)}"
        }

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureLocation = GLES30.glGetUniformLocation(program, "uTexture")
        textureMatrixLocation = GLES30.glGetUniformLocation(program, "uTextureMatrix")
        outputModeLocation = GLES30.glGetUniformLocation(program, "uOutputMode")

        // GLES 3 removed client-side vertex arrays. Keep the quad in an actual VBO/VAO instead
        // of relying on the FloatBuffer overload accepted by older GLES 2 drivers.
        val vertexArrays = IntArray(1)
        GLES30.glGenVertexArrays(1, vertexArrays, 0)
        vertexArrayId = vertexArrays[0]
        GLES30.glBindVertexArray(vertexArrayId)
        val vertexBuffers = IntArray(1)
        GLES30.glGenBuffers(1, vertexBuffers, 0)
        vertexBufferId = vertexBuffers[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId)
        vertices.position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            VERTICES.size * Float.SIZE_BYTES,
            vertices,
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            2,
            GLES30.GL_FLOAT,
            false,
            STRIDE_BYTES,
            2 * Float.SIZE_BYTES,
        )
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        externalTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE,
        )

        val inputTexture = SurfaceTexture(externalTextureId)
        decoderTexture = inputTexture
        inputTexture.setOnFrameAvailableListener(
            {
                if (!released.get()) {
                    runCatching { renderFrame(updateImage = true) }
                        .onFailure(::fail)
                }
            },
            renderHandler,
        )
        val inputSurface = Surface(inputTexture)
        decoderSurface = inputSurface
        mainHandler.post {
            if (released.get()) {
                inputSurface.release()
            } else {
                onDecoderSurfaceReady(inputSurface)
            }
        }
    }

    private fun renderFrame(updateImage: Boolean) {
        if (released.get() || display == EGL14.EGL_NO_DISPLAY) return
        val inputTexture = decoderTexture ?: return
        val firstTarget = eglTargets.firstOrNull() ?: return
        check(
            EGL14.eglMakeCurrent(
                display,
                firstTarget.eglSurface,
                firstTarget.eglSurface,
                context,
            ),
        ) { "eglMakeCurrent failed while updating the decoder texture" }
        if (updateImage) {
            inputTexture.updateTexImage()
            inputTexture.getTransformMatrix(textureMatrix)
        } else if (!firstFrameRendered) {
            return
        }

        eglTargets.forEach { target ->
            check(
                EGL14.eglMakeCurrent(
                    display,
                    target.eglSurface,
                    target.eglSurface,
                    context,
                ),
            ) { "eglMakeCurrent failed while rendering an output layer" }
            val width = IntArray(1)
            val height = IntArray(1)
            EGL14.eglQuerySurface(display, target.eglSurface, EGL14.EGL_WIDTH, width, 0)
            EGL14.eglQuerySurface(display, target.eglSurface, EGL14.EGL_HEIGHT, height, 0)
            GLES30.glViewport(0, 0, width[0], height[0])
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(program)
            GLES30.glBindVertexArray(vertexArrayId)
            GLES30.glUniformMatrix4fv(textureMatrixLocation, 1, false, textureMatrix, 0)
            GLES30.glUniform1i(
                outputModeLocation,
                if (eglTargets.size == 1) output.ordinal else target.descriptor.output.ordinal,
            )
            GLES30.glUniform1i(textureLocation, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glBindVertexArray(0)
            EGLExt.eglPresentationTimeANDROID(
                display,
                target.eglSurface,
                inputTexture.timestamp,
            )
            check(EGL14.eglSwapBuffers(display, target.eglSurface)) {
                "eglSwapBuffers failed: 0x${EGL14.eglGetError().toString(16)}"
            }
        }

        if (!firstFrameRendered) {
            firstFrameRendered = true
            mainHandler.post { if (!released.get()) onFirstFrame() }
        }
    }

    fun release(onReleased: () -> Unit = {}) {
        releaseCallbacks.add(onReleased)
        if (!released.compareAndSet(false, true)) {
            if (cleanupComplete.get()) dispatchReleaseCallbacks()
            return
        }
        if (::renderHandler.isInitialized) {
            renderHandler.post {
                releaseGl()
                renderThread.quitSafely()
                dispatchReleaseCallbacks()
            }
        } else if (renderThread.isAlive) {
            renderThread.quitSafely()
            dispatchReleaseCallbacks()
        } else {
            dispatchReleaseCallbacks()
        }
    }

    private fun releaseGl() {
        decoderTexture?.setOnFrameAvailableListener(null)
        decoderTexture?.release()
        decoderTexture = null
        // MediaPlayer/BootregMatteTextureView owns the corresponding Surface after hand-off.
        decoderSurface = null

        if (display != EGL14.EGL_NO_DISPLAY) {
            val cleanupTarget = eglTargets.firstOrNull()
            if (cleanupTarget != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(
                    display,
                    cleanupTarget.eglSurface,
                    cleanupTarget.eglSurface,
                    context,
                )
                if (program != 0) GLES30.glDeleteProgram(program)
                if (externalTextureId != 0) {
                    GLES30.glDeleteTextures(1, intArrayOf(externalTextureId), 0)
                }
                if (vertexBufferId != 0) {
                    GLES30.glDeleteBuffers(1, intArrayOf(vertexBufferId), 0)
                }
                if (vertexArrayId != 0) {
                    GLES30.glDeleteVertexArrays(1, intArrayOf(vertexArrayId), 0)
                }
            }
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            eglTargets.forEach { target ->
                if (target.eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, target.eglSurface)
                }
                target.window.release()
            }
            eglTargets.clear()
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        } else {
            eglTargets.forEach { it.window.release() }
            eglTargets.clear()
        }
        context = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
        program = 0
        externalTextureId = 0
        vertexBufferId = 0
        vertexArrayId = 0
    }

    private fun fail(throwable: Throwable) {
        if (!released.compareAndSet(false, true)) return
        mainHandler.post { onError(throwable) }
        if (Looper.myLooper() == renderThread.looper) {
            releaseGl()
            renderThread.quitSafely()
            dispatchReleaseCallbacks()
        } else if (::renderHandler.isInitialized) {
            renderHandler.post {
                releaseGl()
                renderThread.quitSafely()
                dispatchReleaseCallbacks()
            }
        } else if (renderThread.isAlive) {
            renderThread.quitSafely()
            dispatchReleaseCallbacks()
        } else {
            dispatchReleaseCallbacks()
        }
    }

    private fun dispatchReleaseCallbacks() {
        cleanupComplete.set(true)
        mainHandler.post {
            while (true) {
                val callback = releaseCallbacks.poll() ?: break
                callback()
            }
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val result = GLES30.glCreateProgram()
        GLES30.glAttachShader(result, vertex)
        GLES30.glAttachShader(result, fragment)
        GLES30.glLinkProgram(result)
        val status = IntArray(1)
        GLES30.glGetProgramiv(result, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(result)
            GLES30.glDeleteProgram(result)
            error("Unable to link matte-video shader: $log")
        }
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Unable to compile matte-video shader: $log")
        }
        return shader
    }

    private companion object {
        const val EGL_OPENGL_ES3_BIT_KHR = 0x40
        const val STRIDE_BYTES = 4 * Float.SIZE_BYTES
        val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
        const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aTextureCoordinate;
            out vec2 vTextureCoordinate;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTextureCoordinate = aTextureCoordinate;
            }
        """
        const val FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision highp float;
            uniform samplerExternalOES uTexture;
            uniform mat4 uTextureMatrix;
            // 0=premultiplied foreground, 1=opaque left colour, 2=visible alpha mask.
            uniform int uOutputMode;
            in vec2 vTextureCoordinate;
            out vec4 outColor;

            vec2 transformedCoordinate(vec2 coordinate) {
                return (uTextureMatrix * vec4(coordinate, 0.0, 1.0)).xy;
            }

            void main() {
                vec2 leftUv = transformedCoordinate(
                    vec2(vTextureCoordinate.x * 0.5, vTextureCoordinate.y)
                );
                vec2 rightUv = transformedCoordinate(
                    vec2(vTextureCoordinate.x * 0.5 + 0.5, vTextureCoordinate.y)
                );
                vec3 color = texture(uTexture, leftUv).rgb;
                float alpha = texture(uTexture, rightUv).r;

                if (uOutputMode == 1) {
                    outColor = vec4(color, 1.0);
                } else if (uOutputMode == 2) {
                    outColor = vec4(vec3(alpha), 1.0);
                } else if (alpha > 0.9) {
                    outColor = vec4(color, 1.0);
                } else {
                    outColor = vec4(color * alpha, alpha);
                }
            }
        """
    }
}
