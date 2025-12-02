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
        private const val STEP_MS = 2000        // Process every 2 seconds (faster response)
        private const val LENGTH_MS = 10000     // Use 10 seconds of audio (more context for accuracy)
        private const val KEEP_MS = 500         // Keep 0.5 second overlap
    }

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var transcriptionText: TextView

    private var whisperContext: WhisperContext? = null
    private val audioRecorder = AudioRecorder()
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null

    // Sliding window state
    private var previousAudio = FloatArray(0)
    private val stepSamples = (STEP_MS * SAMPLE_RATE) / 1000
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

            // Reset sliding window state
            previousAudio = FloatArray(0)

            Log.d(TAG, "Starting streaming recognition: step=${STEP_MS}ms, length=${LENGTH_MS}ms, keep=${KEEP_MS}ms")

            // Sliding window streaming transcription
            recordingJob = scope.launch(Dispatchers.IO) {
                var iterationCount = 0

                while (isActive && isRecording) {
                    // Continuously read audio data
                    audioRecorder.readAudioData()

                    // Get audio with overlap using sliding window
                    val audioData = audioRecorder.getAudioWithOverlap(
                        stepSamples = stepSamples,
                        keepSamples = keepSamples,
                        previousAudio = previousAudio
                    )

                    if (audioData.isNotEmpty()) {
                        iterationCount++
                        Log.d(TAG, "Processing iteration $iterationCount with ${audioData.size} samples")

                        withContext(Dispatchers.Main) {
                            updateStatus("Transcribing...")
                        }

                        try {
                            // Use streaming transcription with context
                            val result = whisperContext?.transcribeStreaming(
                                audioData,
                                numThreads = 4,
                                keepContext = true
                            ) ?: ""

                            Log.d(TAG, "Streaming result: '$result'")

                            if (result.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    val currentText = transcriptionText.text.toString()
                                    transcriptionText.text = if (currentText.isEmpty()) {
                                        result
                                    } else {
                                        "$currentText $result"
                                    }
                                    updateStatus("Recording...")
                                }
                            }

                            // Update previous audio for next iteration
                            previousAudio = audioData
                        } catch (e: Exception) {
                            Log.e(TAG, "Streaming transcription failed", e)
                            withContext(Dispatchers.Main) {
                                updateStatus("Recording...")
                            }
                        }
                    }

                    delay(25) // Read audio every 25ms
                }
            }

            Log.d(TAG, "Recording started with sliding window streaming")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecordingAndTranscribe() {
        recordingJob?.cancel()
        isRecording = false
        recordButton.text = getString(R.string.start_recording)
        updateStatus("Stopping...")

        scope.launch(Dispatchers.IO) {
            try {
                // Stop recording and process any remaining audio
                audioRecorder.stopRecording()
                Log.d(TAG, "Recording stopped")

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