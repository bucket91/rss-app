package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.RssItemEntity
import com.example.data.RssRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class PodcastDownloadService(
    private val context: Context,
    private val repository: RssRepository
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        val podcastDir = File(context.filesDir, "podcasts")
        if (!podcastDir.exists()) {
            podcastDir.mkdirs()
        }
    }

    private fun getPodcastFile(guid: String): File {
        val sanitizedGuid = guid.replace("[^a-zA-Z0-9]".toRegex(), "_")
        return File(File(context.filesDir, "podcasts"), "$sanitizedGuid.mp3")
    }

    fun isDownloaded(item: RssItemEntity): Boolean {
        if (item.localAudioPath == null) return false
        val file = File(item.localAudioPath)
        return file.exists() && file.length() > 0
    }

    fun downloadEpisode(item: RssItemEntity) {
        val guid = item.guid
        val audioUrl = item.audioUrl ?: return

        val currentStates = _downloadStates.value
        if (currentStates[guid] is DownloadState.Downloading || currentStates[guid] is DownloadState.Completed) {
            return
        }

        _downloadStates.value = currentStates + (guid to DownloadState.Downloading(0))

        scope.launch {
            try {
                val outputFile = getPodcastFile(guid)
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                downloadFile(audioUrl, outputFile) { progress ->
                    _downloadStates.value = _downloadStates.value + (guid to DownloadState.Downloading(progress))
                }

                // Update Database
                repository.updateLocalAudioPath(guid, outputFile.absolutePath)

                _downloadStates.value = _downloadStates.value + (guid to DownloadState.Completed)
            } catch (e: Exception) {
                Log.e("PodcastDownload", "Failed to download $guid", e)
                _downloadStates.value = _downloadStates.value + (guid to DownloadState.Failed(e.message ?: "Unknown error"))
            }
        }
    }

    fun deleteEpisode(item: RssItemEntity) {
        scope.launch {
            try {
                val file = getPodcastFile(item.guid)
                if (file.exists()) {
                    file.delete()
                }
                repository.updateLocalAudioPath(item.guid, null)
                _downloadStates.value = _downloadStates.value - item.guid
            } catch (e: Exception) {
                Log.e("PodcastDownload", "Failed to delete file for ${item.guid}", e)
            }
        }
    }

    private suspend fun downloadFile(
        fileUrl: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(fileUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            inputStream = connection.inputStream
            outputStream = FileOutputStream(outputFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            var lastProgress = 0

            while (inputStream.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgress(progress)
                    }
                }
                outputStream.write(data, 0, count)
            }
            outputStream.flush()
        } finally {
            outputStream?.close()
            inputStream?.close()
            connection?.disconnect()
        }
    }
}
