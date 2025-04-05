package com.realme.procamera

import android.opengl.GLES30
import android.util.Log

object ColorManagement {
    private const val TAG = "ColorManagement"
    
    // Supported color profiles
    enum class ColorProfile(val id: Int) {
        REC709(0),
        GAMMA24(1),
        REALLME_LOG(2)
    }

    // Current active profile
    private var currentProfile = ColorProfile.REC709

    // GLSL shader code for color transformations
    private const val VERTEX_SHADER = """
        #version 300 es
        layout(location = 0) in vec4 position;
        layout(location = 1) in vec2 texCoord;
        out vec2 vTexCoord;
        void main() {
            gl_Position = position;
            vTexCoord = texCoord;
        }
    """

    private const val FRAGMENT_SHADER_BASE = """
        #version 300 es
        precision highp float;
        in vec2 vTexCoord;
        uniform sampler2D uTexture;
        out vec4 fragColor;
        
        vec3 applyRec709(vec3 linear) {
            return pow(linear, vec3(1.0/2.4));
        }
        
        vec3 applyGamma24(vec3 linear) {
            return pow(linear, vec3(1.0/2.4));
        }
        
        vec3 applyRealmeLog(vec3 linear) {
            float cut = 0.010591;
            float a = 5.555556, b = 0.052272;
            float c = 0.247190, d = 0.385537;
            return (linear > cut) ? 
                c * log10(a * linear + b) + d : 
                linear * 16.0;
        }
        
        void main() {
            vec4 texColor = texture(uTexture, vTexCoord);
            vec3 linear = texColor.rgb;
    """

    private fun getFragmentShader(profile: ColorProfile): String {
        return FRAGMENT_SHADER_BASE + when (profile) {
            ColorProfile.REC709 -> "fragColor = vec4(applyRec709(linear), texColor.a);\n"
            ColorProfile.GAMMA24 -> "fragColor = vec4(applyGamma24(linear), texColor.a);\n"
            ColorProfile.REALLME_LOG -> "fragColor = vec4(applyRealmeLog(linear), texColor.a);\n"
        } + "}"
    }

    private var programHandle = 0

    fun initialize() {
        programHandle = createProgram(VERTEX_SHADER, getFragmentShader(currentProfile))
        if (programHandle == 0) {
            Log.e(TAG, "Failed to create shader program")
        }
    }

    fun setColorProfile(profile: ColorProfile) {
        if (profile != currentProfile) {
            currentProfile = profile
            GLES30.glDeleteProgram(programHandle)
            programHandle = createProgram(VERTEX_SHADER, getFragmentShader(profile))
        }
    }

    fun getProgramHandle(): Int = programHandle

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertexShaderHandle = compileShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fragmentShaderHandle = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        
        if (vertexShaderHandle == 0 || fragmentShaderHandle == 0) {
            return 0
        }

        val programHandle = GLES30.glCreateProgram()
        if (programHandle != 0) {
            GLES30.glAttachShader(programHandle, vertexShaderHandle)
            GLES30.glAttachShader(programHandle, fragmentShaderHandle)
            GLES30.glLinkProgram(programHandle)
            
            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(programHandle, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error linking program: " + GLES30.glGetProgramInfoLog(programHandle))
                GLES30.glDeleteProgram(programHandle)
                return 0
            }
        }
        return programHandle
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shaderHandle = GLES30.glCreateShader(type)
        if (shaderHandle != 0) {
            GLES30.glShaderSource(shaderHandle, shaderCode)
            GLES30.glCompileShader(shaderHandle)
            
            val compileStatus = IntArray(1)
            GLES30.glGetShaderiv(shaderHandle, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES30.glGetShaderInfoLog(shaderHandle))
                GLES30.glDeleteShader(shaderHandle)
                return 0
            }
        }
        return shaderHandle
    }
}