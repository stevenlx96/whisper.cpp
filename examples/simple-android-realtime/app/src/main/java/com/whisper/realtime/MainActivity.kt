package com.whisper.realtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO = 1
        private const val MODEL_PATH = "models/ggml-tiny.bin"
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

        // Check and request permission
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
                Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initializeWhisper() {
        scope.launch(Dispatchers.IO) {
            try {
                updateStatus("正在加载模型...")
                whisperContext = WhisperContext.createFromAsset(assets, MODEL_PATH)
                withContext(Dispatchers.Main) {
                    updateStatus("准备就绪")
                    Toast.makeText(this@MainActivity, "Whisper 模型加载成功", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "Whisper initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper", e)
                withContext(Dispatchers.Main) {
                    updateStatus("模型加载失败")
                    Toast.makeText(
                        this@MainActivity,
                        "模型加载失败: ${e.message}\n请确保将 ggml-tiny.bin 放入 assets/models/ 目录",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startRecording() {
        if (whisperContext == null) {
            Toast.makeText(this, "Whisper 未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            audioRecorder.startRecording()
            isRecording = true
            recordButton.text = getString(R.string.stop_recording)
            updateStatus("正在录音...")

            // Continuously read audio data in background
            recordingJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRecording) {
                    audioRecorder.readAudioData()
                    delay(100) // Read every 100ms
                }
            }

            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            Toast.makeText(this, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecordingAndTranscribe() {
        recordingJob?.cancel()
        isRecording = false
        recordButton.text = getString(R.string.start_recording)
        updateStatus("正在识别...")

        scope.launch(Dispatchers.IO) {
            try {
                val audioData = audioRecorder.stopRecording()
                Log.d(TAG, "Audio data size: ${audioData.size}")

                if (audioData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "没有录音数据", Toast.LENGTH_SHORT).show()
                        updateStatus("准备就绪")
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
                        Toast.makeText(this@MainActivity, "未识别到内容", Toast.LENGTH_SHORT).show()
                    }
                    updateStatus("准备就绪")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "识别失败: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateStatus("准备就绪")
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
