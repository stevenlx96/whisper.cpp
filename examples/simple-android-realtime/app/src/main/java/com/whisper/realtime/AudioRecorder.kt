package com.whisper.realtime

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Short>()
    private val allRecordedAudio = mutableListOf<Short>()  // Keep all audio for saving

    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            audioBuffer.clear()
            allRecordedAudio.clear()
            audioRecord?.startRecording()
            isRecording = true

            Log.d(TAG, "Recording started with buffer size: ${bufferSize * 2}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            throw e
        }
    }

    fun stopRecording(): FloatArray {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return FloatArray(0)
        }

        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Return any remaining audio in buffer
        val floatArray = FloatArray(audioBuffer.size)
        for (i in audioBuffer.indices) {
            floatArray[i] = audioBuffer[i] / 32768.0f
        }

        Log.d(TAG, "Recording stopped, captured ${floatArray.size} samples in buffer, ${allRecordedAudio.size} samples total")
        return floatArray
    }

    fun stopRecordingAndSavePCM(cacheDir: File): FloatArray {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return FloatArray(0)
        }

        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Get max amplitude for gain calculation
        val maxAmplitude = getMaxAmplitude()
        Log.d(TAG, "Max amplitude: $maxAmplitude")

        val gainFactor = calculateGain(maxAmplitude)
        Log.d(TAG, "Gain factor: $gainFactor")

        // Apply gain to all recorded audio
        val processedBuffer = applyGainToList(allRecordedAudio, gainFactor)
        savePCMToFile(cacheDir, processedBuffer)

        // Convert to float array
        val floatArray = FloatArray(processedBuffer.size)
        for (i in processedBuffer.indices) {
            floatArray[i] = processedBuffer[i] / 32768.0f
        }

        Log.d(TAG, "Recording stopped and saved, captured ${floatArray.size} samples")
        return floatArray
    }

    private fun getMaxAmplitude(): Int {
        if (allRecordedAudio.isEmpty()) return 0
        return allRecordedAudio.maxOfOrNull { abs(it.toInt()) } ?: 0
    }

    private fun calculateGain(maxAmplitude: Int): Float {
        if (maxAmplitude == 0) return 1.0f

        val targetLevel = 20000
        val gain = targetLevel.toFloat() / maxAmplitude.toFloat()

        return min(gain, 3.0f)
    }

    private fun applyGainToList(audioList: List<Short>, gainFactor: Float): ShortArray {
        val result = ShortArray(audioList.size)

        for (i in audioList.indices) {
            val amplified = (audioList[i].toInt() * gainFactor).toInt()
            result[i] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), amplified)).toShort()
        }

        return result
    }

    private fun savePCMToFile(cacheDir: File, pcmData: ShortArray) {
        if (pcmData.isEmpty()) {
            Log.w(TAG, "No audio data to save")
            return
        }

        try {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val pcmFile = File(cacheDir, "audio_${timestamp}.pcm")

            val fos = FileOutputStream(pcmFile)
            val buffer = ByteBuffer.allocate(pcmData.size * 2)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            for (sample in pcmData) {
                buffer.putShort(sample)
            }

            fos.write(buffer.array())
            fos.close()

            val fileSizeKB = pcmFile.length() / 1024
            Log.d(TAG, "PCM saved: ${pcmFile.absolutePath} (${fileSizeKB}KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PCM file", e)
        }
    }

    fun readAudioData() {
        if (!isRecording || audioRecord == null) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        val buffer = ShortArray(bufferSize / 2)
        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0

        if (readCount > 0) {
            synchronized(audioBuffer) {
                for (i in 0 until readCount) {
                    audioBuffer.add(buffer[i])
                    allRecordedAudio.add(buffer[i])  // Also save to complete recording
                }
            }
        }
    }

    fun isRecording(): Boolean = isRecording

    /**
     * Get current audio chunk without stopping recording
     * Returns normalized audio data and clears the buffer
     */
    fun getAudioChunk(): FloatArray {
        if (!isRecording || audioBuffer.isEmpty()) {
            return FloatArray(0)
        }

        synchronized(audioBuffer) {
            val floatArray = FloatArray(audioBuffer.size)
            for (i in audioBuffer.indices) {
                floatArray[i] = audioBuffer[i] / 32768.0f
            }
            audioBuffer.clear()
            Log.d(TAG, "Retrieved audio chunk with ${floatArray.size} samples")
            return floatArray
        }
    }

    /**
     * Get audio data for sliding window processing
     * @param stepSamples: number of new samples to collect
     * @param keepSamples: number of samples to keep from previous data
     * @return audio data with overlap
     */
    fun getAudioWithOverlap(stepSamples: Int, keepSamples: Int, previousAudio: FloatArray): FloatArray {
        if (!isRecording) {
            return FloatArray(0)
        }

        synchronized(audioBuffer) {
            val currentSize = audioBuffer.size

            // If we don't have enough new samples yet, return empty
            if (currentSize < stepSamples) {
                return FloatArray(0)
            }

            // Calculate how many samples to take from previous audio
            val samplesToKeep = minOf(keepSamples, previousAudio.size)

            // Create result array: kept samples + new samples
            val result = FloatArray(samplesToKeep + currentSize)

            // Copy kept samples from previous audio (last N samples)
            if (samplesToKeep > 0) {
                for (i in 0 until samplesToKeep) {
                    result[i] = previousAudio[previousAudio.size - samplesToKeep + i]
                }
            }

            // Copy current buffer (all new samples)
            for (i in 0 until currentSize) {
                result[samplesToKeep + i] = audioBuffer[i] / 32768.0f
            }

            // Clear the buffer for next iteration
            audioBuffer.clear()

            Log.d(TAG, "Retrieved audio with overlap: kept=$samplesToKeep, new=$currentSize, total=${result.size}")
            return result
        }
    }
}
