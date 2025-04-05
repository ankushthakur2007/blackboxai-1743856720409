package com.realme.procamera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

object CameraService {
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraCharacteristics: CameraCharacteristics
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isRecording = false
    private lateinit var imageReader: ImageReader

    // Realme 6 Pro specific constants
    private const val TELEPHOTO_FOCAL_LENGTH = 4.4f // 2x optical zoom
    private val RAW_SIZE = Size(9216, 6912) // 64MP RAW

    fun initialize(context: Context, previewView: PreviewView) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = getBestCameraId()

        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                cameraProvider = future.get()
                bindPreviewUseCase(context as LifecycleOwner, previewView, cameraId)
                setupRawCapture(cameraId)
            }, cameraExecutor)
        }
    }

    private fun getBestCameraId(): String {
        return cameraManager.cameraIdList.first { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }.also {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(it)
        }
    }

    private fun bindPreviewUseCase(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraId: String
    ) {
        val preview = Preview.Builder()
            .setTargetResolution(Size(1920, 1080))
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    private fun setupRawCapture(cameraId: String) {
        imageReader = ImageReader.newInstance(
            RAW_SIZE.width,
            RAW_SIZE.height,
            ImageFormat.RAW_SENSOR,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                // Process RAW images here
            }, cameraExecutor)
        }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val captureRequest = camera.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE
                ).apply {
                    addTarget(imageReader.surface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
                }
                camera.createCaptureSession(
                    listOf(imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, null)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    null
                )
            }
            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, null)
    }

    fun toggleRecording() {
        isRecording = !isRecording
        // Implement recording logic
    }

    fun shutdown() {
        imageReader.close()
        cameraExecutor.shutdown()
    }
}