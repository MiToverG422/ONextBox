package com.mi.onextbox.ui.screens

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.mi.onextbox.R

private const val FeatureBackgroundFadeMillis = 550
private const val FeatureBackgroundSourceFrameRate = 30f

/** Features background playback state, retained across child-page navigation, [restart] starts a new visit. */
@Stable
class FeatureBackgroundPlaybackState {
    private var restartGeneration by mutableIntStateOf(0)
    private var resumePositionMs = 0
    private var knownDurationMs = 0
    private var playbackCompleted = false
    private var frameRendered = false

    internal val generation: Int
        get() = restartGeneration

    /** Starts a fresh one-shot playback, including the initial reveal. */
    fun restart() {
        resumePositionMs = 0
        knownDurationMs = 0
        playbackCompleted = false
        frameRendered = false
        restartGeneration += 1
    }

    internal fun snapshot(generation: Int): FeatureBackgroundPlaybackSnapshot {
        if (generation != restartGeneration) return FeatureBackgroundPlaybackSnapshot.Empty
        return FeatureBackgroundPlaybackSnapshot(
            positionMs = resumePositionMs,
            durationMs = knownDurationMs,
            completed = playbackCompleted,
            hasRenderedFrame = frameRendered,
        )
    }

    internal fun recordProgress(
        generation: Int,
        positionMs: Int,
        durationMs: Int,
        completed: Boolean,
    ) {
        if (generation != restartGeneration) return

        knownDurationMs = maxOf(knownDurationMs, durationMs.coerceAtLeast(0))
        if (playbackCompleted) return

        resumePositionMs = maxOf(resumePositionMs, positionMs.coerceAtLeast(0))
        playbackCompleted = completed
    }

    internal fun recordRenderedFrame(generation: Int) {
        if (generation == restartGeneration) frameRendered = true
    }
}

@Composable
internal fun rememberFeatureBackgroundPlaybackState(): FeatureBackgroundPlaybackState =
    remember { FeatureBackgroundPlaybackState() }

internal data class FeatureBackgroundPlaybackSnapshot(
    val positionMs: Int,
    val durationMs: Int,
    val completed: Boolean,
    val hasRenderedFrame: Boolean,
) {
    companion object {
        val Empty = FeatureBackgroundPlaybackSnapshot(
            positionMs = 0,
            durationMs = 0,
            completed = false,
            hasRenderedFrame = false,
        )
    }
}

/** One-shot Features background, revealed after the first frame and retained at its final frame. */
@Composable
internal fun FeaturePageVideoBackground(
    playbackState: FeatureBackgroundPlaybackState,
    modifier: Modifier = Modifier,
) {
    val generation = playbackState.generation
    val animateReveal = remember(playbackState, generation) {
        !playbackState.snapshot(generation).hasRenderedFrame
    }
    var firstFrameRendered by remember(playbackState, generation) { mutableStateOf(false) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = if (firstFrameRendered && animateReveal) {
            tween(
                durationMillis = FeatureBackgroundFadeMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            // Hide an old generation immediately. Restored frames are also revealed immediately;
            // only the first frame of a genuinely new page visit gets the entrance fade.
            snap()
        },
        label = "featurePageVideoReveal",
    )

    AndroidView(
        factory = { context -> FeaturePageVideoTextureView(context) },
        modifier = modifier.graphicsLayer {
            alpha = videoAlpha
            clip = true
        },
        onRelease = FeaturePageVideoTextureView::release,
        update = { view ->
            // AndroidView's factory callback is retained for the lifetime of the View. Refreshing
            // these lambdas here makes them target the current generation's remembered state.
            view.onPlaybackReset = { firstFrameRendered = false }
            view.onFirstFrameRendered = { firstFrameRendered = true }
            view.bindPlaybackState(playbackState, generation)
            view.playWhenReady = true
        },
    )
}

private class FeaturePageVideoTextureView(
    context: Context,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var playbackState: FeatureBackgroundPlaybackState? = null
    private var playbackGeneration = Int.MIN_VALUE
    private var player: MediaPlayer? = null
    private var playerSurface: Surface? = null
    private var prepared = false
    private var completed = false
    private var restoreSeekPending = false
    private var firstFrameReported = false
    private var released = false
    private var videoWidth = 0
    private var videoHeight = 0

    var onPlaybackReset: () -> Unit = {}
    var onFirstFrameRendered: () -> Unit = {}

    var playWhenReady: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updatePlaybackState()
        }

    init {
        surfaceTextureListener = this
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bindPlaybackState(
        state: FeatureBackgroundPlaybackState,
        generation: Int,
    ) {
        if (playbackState === state && playbackGeneration == generation) return

        // Persist the old binding before swapping it. If restart() already advanced the same state,
        // its generation guard intentionally rejects this obsolete player's late snapshot.
        releasePlayer()
        playbackState = state
        playbackGeneration = generation
        onPlaybackReset()

        if (isAvailable && !released) {
            surfaceTexture?.let(::preparePlayer)
        }
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        if (released) return
        if (playbackState != null) preparePlayer(surfaceTexture)
        updateVideoTransform(width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        updateVideoTransform(width, height)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // This is also delivered for a frame produced by a paused seek, while some platform
        // versions omit MEDIA_INFO_VIDEO_RENDERING_START for that case.
        reportFirstFrameRendered()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePlaybackState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updatePlaybackState()
        persistPlaybackProgress()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updatePlaybackState()
        if (visibility != View.VISIBLE) persistPlaybackProgress()
    }

    fun release() {
        released = true
        releasePlayer()
        onPlaybackReset = {}
        onFirstFrameRendered = {}
    }

    private fun preparePlayer(surfaceTexture: SurfaceTexture) {
        releasePlayer()
        val state = playbackState ?: return
        val generation = playbackGeneration
        val restoreSnapshot = state.snapshot(generation)

        prepared = false
        completed = false
        restoreSeekPending = false
        firstFrameReported = false
        videoWidth = 0
        videoHeight = 0
        onPlaybackReset()

        val surface = Surface(surfaceTexture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The bundled source is a fixed 30 fps stream. Explicitly advertise
            // that cadence so SurfaceFlinger can choose the highest compatible
            // display mode instead of treating this TextureView as unspecified.
            surface.setFrameRate(
                FeatureBackgroundSourceFrameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
            )
        }
        playerSurface = surface
        player = MediaPlayer().apply {
            setSurface(surface)
            resources.openRawResourceFd(R.raw.feature_page_background).use { descriptor ->
                setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
            }
            setVolume(0f, 0f)
            isLooping = false
            setOnPreparedListener { mediaPlayer ->
                if (mediaPlayer !== player) return@setOnPreparedListener

                prepared = true
                this@FeaturePageVideoTextureView.videoWidth = mediaPlayer.videoWidth
                this@FeaturePageVideoTextureView.videoHeight = mediaPlayer.videoHeight
                updateVideoTransform(width, height)

                val restorePositionMs = resolveRestorePosition(
                    snapshot = restoreSnapshot,
                    playerDurationMs = mediaPlayer.duration,
                )
                if (restoreSnapshot.completed || restorePositionMs > 0L) {
                    completed = restoreSnapshot.completed
                    restoreSeekPending = true
                    runCatching {
                        mediaPlayer.seekTo(
                            restorePositionMs,
                            MediaPlayer.SEEK_CLOSEST,
                        )
                    }.onFailure {
                        restoreSeekPending = false
                        // Never fall through to a replay when restoring an already completed run.
                        if (!completed) updatePlaybackState()
                    }
                } else {
                    updatePlaybackState()
                }
            }
            setOnSeekCompleteListener { mediaPlayer ->
                if (mediaPlayer !== player) return@setOnSeekCompleteListener
                restoreSeekPending = false
                if (!completed) updatePlaybackState()
            }
            setOnInfoListener { mediaPlayer, what, _ ->
                if (mediaPlayer === player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    reportFirstFrameRendered()
                }
                false
            }
            setOnCompletionListener { mediaPlayer ->
                if (mediaPlayer !== player) return@setOnCompletionListener

                // Do not reset, seek, hide, or release here. Keeping the completed player attached
                // preserves the actual decoded final frame while this TextureView remains alive.
                completed = true
                persistPlaybackProgress()
            }
            setOnErrorListener { mediaPlayer, _, _ ->
                if (mediaPlayer !== player) return@setOnErrorListener false
                prepared = false
                restoreSeekPending = false
                onPlaybackReset()
                true
            }
            prepareAsync()
        }
    }

    private fun resolveRestorePosition(
        snapshot: FeatureBackgroundPlaybackSnapshot,
        playerDurationMs: Int,
    ): Long {
        val durationMs = maxOf(snapshot.durationMs, playerDurationMs).coerceAtLeast(0)
        if (snapshot.completed && durationMs > 0) {
            // Seeking exactly to duration can produce EOS without submitting a buffer on some
            // decoders. One millisecond inside the stream with SEEK_CLOSEST selects its last frame.
            return (durationMs - 1).coerceAtLeast(0).toLong()
        }
        return if (durationMs > 0) {
            snapshot.positionMs.coerceIn(0, durationMs).toLong()
        } else {
            snapshot.positionMs.coerceAtLeast(0).toLong()
        }
    }

    private fun reportFirstFrameRendered() {
        if (firstFrameReported) return
        firstFrameReported = true
        playbackState?.recordRenderedFrame(playbackGeneration)
        onFirstFrameRendered()
    }

    private fun updatePlaybackState() {
        val mediaPlayer = player ?: return
        if (!prepared || completed || restoreSeekPending) return
        val shouldPlay = playWhenReady &&
            isAttachedToWindow &&
            windowVisibility == View.VISIBLE &&
            visibility == View.VISIBLE
        runCatching {
            if (shouldPlay && !mediaPlayer.isPlaying) {
                mediaPlayer.start()
            } else if (!shouldPlay && mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                persistPlaybackProgress()
            }
        }
    }

    private fun persistPlaybackProgress() {
        val mediaPlayer = player ?: return
        if (!prepared && !completed) return

        val positionMs = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
        val durationMs = runCatching { mediaPlayer.duration }.getOrDefault(0)
        playbackState?.recordProgress(
            generation = playbackGeneration,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed,
        )
    }

    private fun updateVideoTransform(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val matrix = Matrix()
        if (videoAspectRatio > viewAspectRatio) {
            matrix.setScale(
                videoAspectRatio / viewAspectRatio,
                1f,
                viewWidth / 2f,
                viewHeight / 2f,
            )
        } else {
            matrix.setScale(
                1f,
                viewAspectRatio / videoAspectRatio,
                viewWidth / 2f,
                viewHeight / 2f,
            )
        }
        setTransform(matrix)
    }

    private fun releasePlayer() {
        persistPlaybackProgress()
        prepared = false
        restoreSeekPending = false
        runCatching { player?.setSurface(null) }
        runCatching { player?.release() }
        player = null
        playerSurface?.release()
        playerSurface = null
    }
}
