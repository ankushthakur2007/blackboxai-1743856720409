package com.realme.procamera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Size
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoRecorder private constructor() {
    companion object {
        private const val TAG = "VideoRecorder"
        private const val TIMEOUT_US = 10000L
        private const val FRAME_RATE = 30
        private const val IFRAME_INTERVAL = 1
        private const val BIT_RATE = 20000000 // 20 Mbps

        fun create(
            outputPath: String,
            resolution: Size,
            codec: Codec = Codec.H264,
            profile: ColorManagement.ColorProfile = ColorManagement.ColorProfile.REC709
        ): VideoRecorder {
            return VideoRecorder().apply {
                this.outputPath = outputPath
                this.resolution = resolution
                this.codec = codec
                this.colorProfile = profile
            }
        }
    }

    enum class Codec(val mimeType: String) {
        H264("video/avc"),
        HEVC("video/hevc")
    }

    private var outputPath: String = ""
    private var resolution: Size = Size(1920, 1080)
    private var codec: Codec = Codec.H264
    private var colorProfile: ColorManagement.ColorProfile = ColorManagement.ColorProfile.REC709
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var trackIndex = -1
    private var isRunning = AtomicBoolean(false)
    private var frameCount = 0

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)

        try {
            // Configure media format
            val format = MediaFormat.createVideoFormat(codec.mimeType, resolution.width, resolution.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
                setString(MediaFormat.KEY_COLOR_STANDARD, when (colorProfile) {
                    ColorManagement.ColorProfile.REC709 -> MediaFormat.COLOR_STANDARD_BT709
                    ColorManagement.ColorProfile.GAMMA24 -> MediaFormat.COLOR_STANDARD_BT709
                    ColorManagement.ColorProfile.REALLME_LOG -> "RealmeLog-C"
                })
            }

            // Create and configure media codec
            mediaCodec = MediaCodec.createEncoderByType(codec.mimeType).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // Create media muxer
            mediaMuxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        if (!isRunning.get()) return
        isRunning.set(false)

        mediaCodec?.stop()
        mediaCodec?.release()
        mediaMuxer?.stop()
        mediaMuxer?.release()
        mediaCodec = null
        mediaMuxer = null
        trackIndex = -1
        frameCount = 0
    }

    fun isRunning(): Boolean = isRunning.get()

    fun getInputSurface() = mediaCodec?.inputSurface

    fun drainEncoder(endOfStream: Boolean = false) {
        if (mediaCodec == null || mediaMuxer == null) return

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferId = mediaCodec!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when (outputBufferId) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) break
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = mediaMuxer!!.addTrack(mediaCodec!!.outputFormat)
                    mediaMuxer!!.start()
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> continue
                else -> {
                    if (outputBufferId >= 0) {
                        val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferId)
                            ?: throw IllegalStateException("Encoder output buffer $outputBufferId was null")

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            bufferInfo.presentationTimeUs = computePresentationTime(frameCount++)
                            mediaMuxer!!.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }

                        mediaCodec!!.releaseOutputBuffer(outputBufferId, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    }
                }
            }
        }
    }

    private fun computePresentationTime(frameNumber: Int): Long {
        return (frameNumber * 1000000L / FRAME_RATE)
    }
}