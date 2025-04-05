package com.realme.procamera

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private var textureHandle = -1
    private var vertexBufferHandle = -1
    private var texCoordBufferHandle = -1
    private var initialized = false

    init {
        setEGLContextClientVersion(3)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        ColorManagement.initialize()
        LUTLoader.loadLUTFromAssets(context, "luts/rec709.cube")
        setupBuffers()
        initialized = true
    }

    private fun setupBuffers() {
        // Vertex coordinates (x, y)
        val vertices = floatArrayOf(
            -1.0f, -1.0f,  // bottom left
            1.0f, -1.0f,   // bottom right
            -1.0f, 1.0f,   // top left
            1.0f, 1.0f     // top right
        )

        // Texture coordinates (s, t)
        val texCoords = floatArrayOf(
            0.0f, 0.0f,  // bottom left
            1.0f, 0.0f,  // bottom right
            0.0f, 1.0f,  // top left
            1.0f, 1.0f   // top right
        )

        // Create vertex buffer
        val vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .rewind()

        // Create texture coordinate buffer
        val texCoordBuffer = ByteBuffer
            .allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
            .rewind()

        // Generate buffers
        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vertexBufferHandle = buffers[0]
        texCoordBufferHandle = buffers[1]

        // Bind and upload vertex data
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferHandle)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Bind and upload texture coordinate data
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBufferHandle)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            texCoords.size * 4,
            texCoordBuffer,
            GLES30.GL_STATIC_DRAW
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!initialized) return

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val programHandle = ColorManagement.getProgramHandle()
        GLES30.glUseProgram(programHandle)

        // Bind vertex attributes
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferHandle)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        // Bind texture coordinates
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, texCoordBufferHandle)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, 0)

        // Bind LUT texture
        LUTLoader.bindLUTTexture(programHandle, 1)

        // Draw
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // Clean up
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun setColorProfile(profile: ColorManagement.ColorProfile) {
        queueEvent {
            ColorManagement.setColorProfile(profile)
            requestRender()
        }
    }

    fun release() {
        queueEvent {
            LUTLoader.release()
            if (vertexBufferHandle != -1) {
                GLES30.glDeleteBuffers(1, intArrayOf(vertexBufferHandle), 0)
                vertexBufferHandle = -1
            }
            if (texCoordBufferHandle != -1) {
                GLES30.glDeleteBuffers(1, intArrayOf(texCoordBufferHandle), 0)
                texCoordBufferHandle = -1
            }
        }
    }
}