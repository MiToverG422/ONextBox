package com.mi.onextbox.ui.onboarding

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import kotlin.math.min

/** How a decoded boot animation frame is fitted into its host. */
internal enum class BootregVideoScaleMode {
    Fit,
    Crop,
    Stretch,
}

/**
 * Local H.264 player, not for side-by-side colour/matte videos, retains the final frame.
 * @param playCount Total plays, [Int.MAX_VALUE] enables continuous looping.
 */
@Composable
internal fun BootregVideoPlayer(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier,
    scaleMode: BootregVideoScaleMode = BootregVideoScaleMode.Crop,
    playCount: Int = 1,
    autoPlay: Boolean = true,
    visible: Boolean = true,
    backgroundColor: Color = Color.Black,
    onFirstFrame: () -> Unit = {},
    onCompleted: () -> Unit = {},
    onError: (what: Int, extra: Int) -> Unit = { _, _ -> },
) {
    require(playCount > 0) { "playCount must be positive" }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentCompleted by rememberUpdatedState(onCompleted)
    val currentError by rememberUpdatedState(onError)
    val currentAutoPlay by rememberUpdatedState(autoPlay)
    val currentVisible by rememberUpdatedState(visible)
    val textureView = remember(context, videoResId) {
        TextureView(context).apply {
            isOpaque = false
            alpha = 0f
        }
    }
    val controller = remember(context, textureView, videoResId, playCount) {
        BootregVideoController(
            context = context,
            textureView = textureView,
            videoResId = videoResId,
            playCount = playCount,
            onFirstFrame = { currentFirstFrame() },
            onCompleted = { currentCompleted() },
            onError = { what, extra -> currentError(what, extra) },
        )
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    controller.setPlaybackAllowed(currentAutoPlay && currentVisible)
                Lifecycle.Event.ON_STOP -> controller.setPlaybackAllowed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        controller.setVisible(visible)
        controller.setPlaybackAllowed(
            autoPlay && visible &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    SideEffect {
        controller.setVisible(visible)
        controller.setPlaybackAllowed(
            autoPlay && visible &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(backgroundColor),
    ) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize(),
            update = { controller.setScaleMode(scaleMode) },
        )
    }
}

private class BootregVideoController(
    context: Context,
    private val textureView: TextureView,
    @RawRes private val videoResId: Int,
    private val playCount: Int,
    private val onFirstFrame: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: (what: Int, extra: Int) -> Unit,
) : TextureView.SurfaceTextureListener, View.OnLayoutChangeListener {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var playbackAllowed = false
    private var completed = false
    private var firstFrameReported = false
    private var completedPlayCount = 0
    private var layerVisible = true
    private var videoWidth = 0
    private var videoHeight = 0
    private var scaleMode = BootregVideoScaleMode.Crop

    init {
        textureView.surfaceTextureListener = this
        textureView.addOnLayoutChangeListener(this)
        textureView.surfaceTexture?.takeIf { textureView.isAvailable }?.let(::openPlayer)
    }

    fun setScaleMode(value: BootregVideoScaleMode) {
        if (scaleMode == value) return
        scaleMode = value
        updateTransform()
    }

    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        val player = mediaPlayer ?: return
        if (!prepared || completed) return
        runCatching {
            if (allowed) {
                player.start()
            } else if (player.isPlaying) {
                player.pause()
            }
        }
    }

    fun setVisible(visible: Boolean) {
        layerVisible = visible
        if (visible) {
            textureView.visibility = View.VISIBLE
            textureView.alpha = if (firstFrameReported) 1f else 0f
        } else {
            // Hide synchronously without tearing down MediaPlayer. AnimatedContent keeps the old
            // page briefly, so releasing here can block the UI thread and leave a stale frame.
            textureView.alpha = 0f
            textureView.visibility = View.INVISIBLE
        }
    }

    private fun openPlayer(surfaceTexture: SurfaceTexture) {
        if (mediaPlayer != null) return
        val outputSurface = Surface(surfaceTexture)
        surface = outputSurface
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            appContext.resources.openRawResourceFd(videoResId).use { descriptor ->
                player.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            player.setSurface(outputSurface)
            player.setVolume(0f, 0f)
            player.isLooping = playCount == Int.MAX_VALUE
            player.setOnPreparedListener {
                prepared = true
                if (playbackAllowed) it.start()
            }
            player.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                surfaceTexture.setDefaultBufferSize(width, height)
                updateTransform()
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    textureView.visibility = if (layerVisible) View.VISIBLE else View.INVISIBLE
                    textureView.alpha = if (layerVisible) 1f else 0f
                    if (!firstFrameReported) {
                        firstFrameReported = true
                        onFirstFrame()
                    }
                }
                false
            }
            player.setOnCompletionListener {
                completedPlayCount++
                if (completedPlayCount < playCount) {
                    it.seekTo(0)
                    if (playbackAllowed) it.start()
                } else {
                    completed = true
                    onCompleted()
                }
            }
            player.setOnErrorListener { _, what, extra ->
                onError(what, extra)
                true
            }
            player.prepareAsync()
        } catch (throwable: Throwable) {
            release()
            onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        }
    }

    private fun updateTransform() {
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

        val transform = Matrix()
        if (scaleMode != BootregVideoScaleMode.Stretch) {
            val widthRatio = viewWidth / videoWidth.toFloat()
            val heightRatio = viewHeight / videoHeight.toFloat()
            val uniformRatio = when (scaleMode) {
                BootregVideoScaleMode.Fit -> min(widthRatio, heightRatio)
                BootregVideoScaleMode.Crop -> max(widthRatio, heightRatio)
                BootregVideoScaleMode.Stretch -> error("Handled above")
            }
            val scaleX = uniformRatio / widthRatio
            val scaleY = uniformRatio / heightRatio
            transform.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(transform)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openPlayer(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        updateTransform()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releasePlayer()
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
        textureView.removeOnLayoutChangeListener(this)
        if (textureView.surfaceTextureListener === this) {
            textureView.surfaceTextureListener = null
        }
        releasePlayer()
    }

    private fun releasePlayer() {
        prepared = false
        textureView.alpha = 0f
        textureView.visibility = View.INVISIBLE
        mediaPlayer?.runCatching {
            setSurface(null)
            reset()
            release()
        }
        mediaPlayer = null
        surface?.release()
        surface = null
    }
}
