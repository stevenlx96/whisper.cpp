package com.whisper.realtime

import android.content.res.AssetManager
import android.os.Build
import android.util.Log

class WhisperLib {
    companion object {
        private const val TAG = "WhisperLib"

        init {
            try {
                System.loadLibrary("whisper")
                Log.d(TAG, "Whisper library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load whisper library", e)
            }
        }

        // JNI methods
        @JvmStatic
        external fun initContext(modelPath: String): Long

        @JvmStatic
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long

        @JvmStatic
        external fun freeContext(contextPtr: Long)

        @JvmStatic
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray): Int

        @JvmStatic
        external fun fullTranscribeWithContext(contextPtr: Long, numThreads: Int, audioData: FloatArray, keepContext: Boolean): Int

        @JvmStatic
        external fun getTextSegmentCount(contextPtr: Long): Int

        @JvmStatic
        external fun getTextSegment(contextPtr: Long, index: Int): String

        @JvmStatic
        external fun getSystemInfo(): String
    }
}

class WhisperContext(private var ptr: Long) {
    fun transcribe(audioData: FloatArray, numThreads: Int = 4): String {
        if (ptr == 0L) {
            throw RuntimeException("Context is not initialized")
        }

        WhisperLib.fullTranscribe(ptr, numThreads, audioData)
        val segmentCount = WhisperLib.getTextSegmentCount(ptr)

        return buildString {
            for (i in 0 until segmentCount) {
                append(WhisperLib.getTextSegment(ptr, i))
                if (i < segmentCount - 1) {
                    append(" ")
                }
            }
        }
    }

    fun transcribeStreaming(audioData: FloatArray, numThreads: Int = 4, keepContext: Boolean = true): String {
        if (ptr == 0L) {
            throw RuntimeException("Context is not initialized")
        }

        WhisperLib.fullTranscribeWithContext(ptr, numThreads, audioData, keepContext)
        val segmentCount = WhisperLib.getTextSegmentCount(ptr)

        return buildString {
            for (i in 0 until segmentCount) {
                append(WhisperLib.getTextSegment(ptr, i))
                if (i < segmentCount - 1) {
                    append(" ")
                }
            }
        }
    }

    fun release() {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    companion object {
        fun createFromAsset(assetManager: AssetManager, modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, modelPath)
            if (ptr == 0L) {
                throw RuntimeException("Failed to initialize Whisper context from asset: $modelPath")
            }
            return WhisperContext(ptr)
        }

        fun createFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("Failed to initialize Whisper context from file: $filePath")
            }
            return WhisperContext(ptr)
        }
    }
}
