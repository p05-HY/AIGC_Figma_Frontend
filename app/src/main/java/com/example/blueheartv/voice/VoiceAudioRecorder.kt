package com.example.blueheartv.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

const val VOICE_PCM_SAMPLE_RATE = 16_000
const val VOICE_PCM_FORMAT = "pcm"
const val MIN_VOICE_PCM_BYTES = 6_400

class VoiceAudioRecorder {
    private val lock = Any()
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var output = ByteArrayOutputStream()

    val isRecording: Boolean
        get() = captureJob?.isActive == true

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onAmplitudeChanged: (Float) -> Unit = {},
    ): Boolean {
        if (isRecording) return false
        val minBufferSize = AudioRecord.getMinBufferSize(
            VOICE_PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return false

        val bufferSize = max(minBufferSize, 2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            VOICE_PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        synchronized(lock) {
            output = ByteArrayOutputStream()
        }
        return runCatching {
            recorder.startRecording()
            audioRecord = recorder
            captureJob = scope.launch(Dispatchers.IO) {
                captureLoop(recorder, bufferSize, onAmplitudeChanged)
            }
            true
        }.getOrElse {
            recorder.release()
            false
        }
    }

    suspend fun stop(): ByteArray {
        val recorder = audioRecord
        runCatching { recorder?.stop() }
        captureJob?.cancelAndJoin()
        releaseRecorder()
        return synchronized(lock) { output.toByteArray() }
    }

    fun cancel() {
        runCatching { audioRecord?.stop() }
        captureJob?.cancel()
        releaseRecorder()
        synchronized(lock) {
            output = ByteArrayOutputStream()
        }
    }

    private suspend fun captureLoop(
        recorder: AudioRecord,
        bufferSize: Int,
        onAmplitudeChanged: (Float) -> Unit,
    ) {
        val buffer = ByteArray(bufferSize)
        while (currentCoroutineContext().isActive) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) break
            synchronized(lock) {
                output.write(buffer, 0, read)
            }
            val amplitude = pcmAmplitude(buffer, read)
            withContext(Dispatchers.Main.immediate) {
                onAmplitudeChanged(amplitude)
            }
        }
    }

    private fun releaseRecorder() {
        runCatching { audioRecord?.release() }
        audioRecord = null
        captureJob = null
    }
}

private fun pcmAmplitude(buffer: ByteArray, length: Int): Float {
    var sum = 0.0
    var samples = 0
    var index = 0
    while (index + 1 < length) {
        val low = buffer[index].toInt() and 0xff
        val high = buffer[index + 1].toInt()
        val sample = (high shl 8) or low
        sum += sample.toDouble() * sample.toDouble()
        samples += 1
        index += 2
    }
    if (samples == 0) return 0f
    val rms = sqrt(sum / samples).coerceAtLeast(1.0)
    val db = 20.0 * log10(rms / Short.MAX_VALUE) + 60.0
    return db.toFloat().coerceIn(0f, 12f)
}
