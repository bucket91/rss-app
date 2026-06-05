package com.example.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.data.RssItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackInfo(
    val item: RssItemEntity? = null,
    val isPlaying: Boolean = false,
    val progressMs: Int = 0,
    val durationMs: Int = 0,
    val isPrepared: Boolean = false,
    val errorMessage: String? = null
)

class PodcastPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    
    private val _playbackState = MutableStateFlow(PlaybackInfo())
    val playbackState: StateFlow<PlaybackInfo> = _playbackState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    fun playEpisode(item: RssItemEntity) {
        val path = if (item.localAudioPath != null && File(item.localAudioPath).exists()) {
            item.localAudioPath
        } else {
            item.audioUrl
        }

        if (path.isNullOrEmpty()) {
            _playbackState.value = PlaybackInfo(item = item, errorMessage = "Audio source is missing.")
            return
        }

        val currentInfo = _playbackState.value
        if (currentInfo.item?.guid == item.guid && currentInfo.isPrepared) {
            if (mediaPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        _playbackState.value = PlaybackInfo(item = item, isPlaying = false, isPrepared = false)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.value = PlaybackInfo(
                        item = item,
                        isPlaying = true,
                        durationMs = mp.duration,
                        isPrepared = true
                    )
                    startProgressTracker()
                }
                setOnCompletionListener {
                    stopProgressTracker()
                    _playbackState.value = PlaybackInfo(
                        item = item,
                        isPlaying = false,
                        progressMs = 0,
                        durationMs = duration,
                        isPrepared = true
                    )
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("PodcastPlayer", "MediaPlayer Error: what($what) extra($extra)")
                    _playbackState.value = PlaybackInfo(
                        item = item,
                        errorMessage = "Failed to load audio stream."
                    )
                    stopProgressTracker()
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayer", "Exception in playEpisode", e)
            _playbackState.value = PlaybackInfo(item = item, errorMessage = "Error playing track: ${e.localizedMessage}")
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressTracker()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startProgressTracker()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
            }
        }
    }

    fun stop() {
        stopProgressTracker()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e("PodcastPlayer", "Error releasing player", e)
            }
        }
        mediaPlayer = null
        _playbackState.value = PlaybackInfo()
    }

    fun seekTo(progressMs: Int) {
        mediaPlayer?.let {
            it.seekTo(progressMs)
            _playbackState.value = _playbackState.value.copy(progressMs = progressMs)
        }
    }

    fun skipForward() {
        mediaPlayer?.let {
            val target = (it.currentPosition + 15000).coerceAtMost(it.duration)
            it.seekTo(target)
            _playbackState.value = _playbackState.value.copy(progressMs = target)
        }
    }

    fun skipBackward() {
        mediaPlayer?.let {
            val target = (it.currentPosition - 15000).coerceAtLeast(0)
            it.seekTo(target)
            _playbackState.value = _playbackState.value.copy(progressMs = target)
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            progressMs = it.currentPosition
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
