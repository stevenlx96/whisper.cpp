package com.whisper.realtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO = 1

        // Streaming parameters (following stream.cpp design)
        private const val SAMPLE_RATE = 16000
        private const val STEP_MS = 1500        // Process every 1.5 seconds (fast response)
        private const val LENGTH_MS = 10000     // Use 10 seconds of audio window (more context)
        private const val KEEP_MS = 200         // Keep 0.2 second overlap
    }

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var transcriptionText: TextView

    private var whisperContext: WhisperContext? = null
    private val audioRecorder = AudioRecorder()
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    // Sliding window state
    private var allRecordedAudioFloat = mutableListOf<Float>()  // Fixed-size sliding window
    private val stepSamples = (STEP_MS * SAMPLE_RATE) / 1000
    private val lengthSamples = (LENGTH_MS * SAMPLE_RATE) / 1000
    private val keepSamples = (KEEP_MS * SAMPLE_RATE) / 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)
        transcriptionText = findViewById(R.id.transcriptionText)

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecordingAndTranscribe()
            } else {
                startRecording()
            }
        }

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            initializeWhisper()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeWhisper()
            } else {
                Toast.makeText(this, "Record audio permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initializeWhisper() {
        scope.launch(Dispatchers.IO) {
            try {
                updateStatus("Loading model...")

                val modelsDir = File(filesDir, "whisper_models")
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs()
                }

                Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")

                val modelFile = modelsDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".bin")
                }?.firstOrNull()

                if (modelFile == null) {
                    withContext(Dispatchers.Main) {
                        updateStatus("No model found")
                        Toast.makeText(
                            this@MainActivity,
                            "No .bin model file found in:\n${modelsDir.absolutePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                Log.d(TAG, "Loading model from: ${modelFile.absolutePath}")
                updateStatus("Loading: ${modelFile.name}")

                whisperContext = WhisperContext.createFromFile(modelFile.absolutePath)

                withContext(Dispatchers.Main) {
                    updateStatus("Ready to record")
                    Toast.makeText(this@MainActivity, "Model loaded", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "Whisper initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Load failed")
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startRecording() {
        if (whisperContext == null) {
            Toast.makeText(this, "Whisper not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            audioRecorder.startRecording()
            isRecording = true
            recordButton.text = getString(R.string.stop_recording)
            updateStatus("Recording...")

            // Reset accumulation buffer
            allRecordedAudioFloat.clear()

            Log.d(TAG, "Starting streaming recognition: step=${STEP_MS}ms")

            // Audio reading coroutine - runs continuously without blocking
            recordingJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRecording) {
                    audioRecorder.readAudioData()
                    delay(10) // Read audio every 10ms to avoid missing data
                }
            }

            // Audio processing coroutine - re-transcribes fixed-size sliding window
            processingJob = scope.launch(Dispatchers.IO) {
                var iterationCount = 0
                var lastProcessTime = System.currentTimeMillis()

                while (isActive && isRecording) {
                    val currentTime = System.currentTimeMillis()

                    // Continuously accumulate audio samples
                    val currentAudio = audioRecorder.getAudioChunk()
                    if (currentAudio.isNotEmpty()) {
                        synchronized(allRecordedAudioFloat) {
                            for (sample in currentAudio) {
                                allRecordedAudioFloat.add(sample)
                            }

                            // Keep only the most recent LENGTH_MS seconds of audio
                            while (allRecordedAudioFloat.size > lengthSamples) {
                                allRecordedAudioFloat.removeAt(0)
                            }
                        }
                    }

                    // Process every STEP_MS milliseconds
                    if (currentTime - lastProcessTime >= STEP_MS) {
                        // Get current audio window
                        val audioToProcess = synchronized(allRecordedAudioFloat) {
                            allRecordedAudioFloat.toFloatArray()
                        }

                        if (audioToProcess.size >= stepSamples) {  // At least STEP_MS of audio
                            iterationCount++
                            val durationSec = audioToProcess.size.toFloat() / SAMPLE_RATE
                            Log.d(TAG, "Processing iteration $iterationCount: ${audioToProcess.size} samples (%.1fs)".format(durationSec))

                            withContext(Dispatchers.Main) {
                                updateStatus("Transcribing...")
                            }

                            try {
                                // Re-transcribe the entire current window
                                // Each iteration refines the result with the sliding window context
                                val result = whisperContext?.transcribeStreaming(
                                    audioToProcess,
                                    numThreads = 4,
                                    keepContext = false
                                ) ?: ""

                                Log.d(TAG, "Result: '$result'")

                                withContext(Dispatchers.Main) {
                                    // Replace entire text (allows refinement as more audio arrives)
                                    transcriptionText.text = result
                                    updateStatus("Recording...")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Streaming transcription failed", e)
                                withContext(Dispatchers.Main) {
                                    updateStatus("Recording...")
                                }
                            }
                        }

                        lastProcessTime = currentTime
                    }

                    delay(50) // Check frequently for smooth accumulation
                }
            }

            Log.d(TAG, "Recording started with cumulative re-transcription")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecordingAndTranscribe() {
        // Cancel both coroutines
        recordingJob?.cancel()
        processingJob?.cancel()
        isRecording = false
        recordButton.text = getString(R.string.start_recording)
        updateStatus("Stopping...")

        scope.launch(Dispatchers.IO) {
            try {
                // Stop recording and save PCM to cache
                val cacheDir = File(cacheDir, "pcm_recordings")
                audioRecorder.stopRecordingAndSavePCM(cacheDir)
                Log.d(TAG, "Recording stopped and saved to cache")

                withContext(Dispatchers.Main) {
                    updateStatus("Ready to record")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Ready to record")
                }
            }
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            statusText.text = status
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        whisperContext?.release()
        if (isRecording) {
            audioRecorder.stopRecording()
        }
    }
}