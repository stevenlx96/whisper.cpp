package com.whisper.realtime

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000 // Whisper requires 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = mutableListOf<Short>()

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
                bufferSize
            )

            audioBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true

            Log.d(TAG, "Recording started")
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

        // Convert accumulated audio data to float array normalized to [-1, 1]
        val floatArray = FloatArray(audioBuffer.size)
        for (i in audioBuffer.indices) {
            floatArray[i] = audioBuffer[i] / 32768.0f
        }

        Log.d(TAG, "Recording stopped, captured ${floatArray.size} samples")
        return floatArray
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
            for (i in 0 until readCount) {
                audioBuffer.add(buffer[i])
            }
        }
    }

    fun isRecording(): Boolean = isRecording
}
