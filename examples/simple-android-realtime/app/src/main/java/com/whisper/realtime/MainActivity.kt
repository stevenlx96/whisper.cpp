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
    }

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var transcriptionText: TextView

    private var whisperContext: WhisperContext? = null
    private val audioRecorder = AudioRecorder()
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null

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

            recordingJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRecording) {
                    audioRecorder.readAudioData()
                    delay(25)
                }
            }

            Log.d(TAG, "Recording started")
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
        updateStatus("Transcribing...")

        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(cacheDir, "pcm_recordings")
                val audioData = audioRecorder.stopRecordingAndSavePCM(cacheDir)
                Log.d(TAG, "Audio data size: ${audioData.size}")

                if (audioData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No audio data", Toast.LENGTH_SHORT).show()
                        updateStatus("Ready to record")
                    }
                    return@launch
                }

                val result = whisperContext?.transcribe(audioData, numThreads = 4) ?: ""
                Log.d(TAG, "Transcription result: $result")

                withContext(Dispatchers.Main) {
                    if (result.isNotEmpty()) {
                        val currentText = transcriptionText.text.toString()
                        transcriptionText.text = if (currentText.isEmpty()) {
                            result
                        } else {
                            "$currentText\n\n$result"
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "No text recognized", Toast.LENGTH_SHORT).show()
                    }
                    updateStatus("Ready to record")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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