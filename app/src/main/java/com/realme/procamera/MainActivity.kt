package com.realme.procamera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.realme.procamera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var fabRecord: FloatingActionButton
    private var videoRecorder: VideoRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        glSurfaceView = binding.preview
        fabRecord = binding.fabRecord

        // Initialize camera service
        CameraService.initialize(this, glSurfaceView)

        // Set up record button
        fabRecord.setOnClickListener {
            toggleRecording()
        }

        // Set up color profile controls
        setupColorProfileControls()
    }

    private fun toggleRecording() {
        if (videoRecorder?.isRunning() == true) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            videoRecorder = VideoRecorder.create(
                outputPath = getOutputMediaPath(),
                resolution = Size(3840, 2160), // 4K
                codec = if (supportsHevc()) VideoRecorder.Codec.HEVC else VideoRecorder.Codec.H264,
                profile = ColorManagement.ColorProfile.REALLME_LOG
            ).apply {
                start()
                CameraService.setRecordingSurface(getInputSurface())
            }

            fabRecord.setImageResource(R.drawable.ic_stop)
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        try {
            videoRecorder?.stop()
            CameraService.clearRecordingSurface()
            fabRecord.setImageResource(R.drawable.ic_record)
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop recording: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            videoRecorder = null
        }
    }

    private fun setupColorProfileControls() {
        // Implement color profile selection UI
        // This would connect to your color profile selection UI elements
    }

    private fun getOutputMediaPath(): String {
        // Implement proper media file path generation
        return "${getExternalFilesDir(null)}/recording_${System.currentTimeMillis()}.mp4"
    }

    private fun supportsHevc(): Boolean {
        // Check for HEVC support
        return false // Simplified for this example
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        CameraService.shutdown()
        glSurfaceView.release()
    }
}