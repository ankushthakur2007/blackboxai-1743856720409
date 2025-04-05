package com.realme.procamera

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object LUTLoader {
    private const val TAG = "LUTLoader"
    private const val LUT_SIZE = 33 // Standard .cube LUT size (33x33x33)
    private var lutTextureHandle = -1

    fun loadLUTFromAssets(context: Context, filename: String): Boolean {
        try {
            context.assets.open(filename).use { inputStream ->
                return parseCubeFile(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading LUT: ${e.message}")
            return false
        }
    }

    private fun parseCubeFile(inputStream: InputStream): Boolean {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var size = 0
        val data = mutableListOf<Float>()

        reader.useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("TITLE") -> {} // Skip title
                    line.startsWith("LUT_3D_SIZE") -> {
                        size = line.split(" ").last().toInt()
                        if (size != LUT_SIZE) {
                            throw IllegalArgumentException("Only 33x33x33 LUTs are supported")
                        }
                    }
                    line.matches(Regex("\\d+\\.\\d+ \\d+\\.\\d+ \\d+\\.\\d+")) -> {
                        val values = line.split(" ")
                        data.add(values[0].toFloat()) // R
                        data.add(values[1].toFloat()) // G
                        data.add(values[2].toFloat()) // B
                    }
                }
            }
        }

        if (data.size != LUT_SIZE * LUT_SIZE * LUT_SIZE * 3) {
            throw IllegalArgumentException("Invalid LUT data size")
        }

        uploadToGPU(data.toFloatArray())
        return true
    }

    private fun uploadToGPU(data: FloatArray) {
        val buffer = ByteBuffer
            .allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .rewind()

        val textureHandles = IntArray(1)
        GLES30.glGenTextures(1, textureHandles, 0)
        lutTextureHandle = textureHandles[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureHandle)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGB16F,
            LUT_SIZE,
            LUT_SIZE,
            LUT_SIZE,
            0,
            GLES30.GL_RGB,
            GLES30.GL_FLOAT,
            buffer
        )

        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    fun bindLUTTexture(programHandle: Int, textureUnit: Int) {
        if (lutTextureHandle == -1) return

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + textureUnit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureHandle)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(programHandle, "uLUT"),
            textureUnit
        )
    }

    fun release() {
        if (lutTextureHandle != -1) {
            GLES30.glDeleteTextures(1, intArrayOf(lutTextureHandle), 0)
            lutTextureHandle = -1
        }
    }
}