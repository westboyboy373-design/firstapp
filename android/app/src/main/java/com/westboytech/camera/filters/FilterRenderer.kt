package com.westboytech.camera.filters

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GL_TEXTURE_EXTERNAL_OES-based renderer: takes the CameraX analysis
 * frame stream (via a SurfaceTexture) and renders it through
 * filter_fragment.glsl into whatever target surface is currently bound
 * — the on-screen preview EGL surface, or the MediaCodec encoder's input
 * surface for export. Same shader program drives both, so preview and
 * export always match exactly, per spec.
 */
class FilterRenderer(context: Context, rawResId_vertex: Int, rawResId_fragment: Int) {

    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var texMatrixHandle = 0
    private var filterTypeHandle = 0
    private var timeHandle = 0
    private var letterboxHandle = 0

    var currentFilter: FilterType = FilterType.NONE
    var letterboxEnabled: Boolean = false
    private val texMatrix = FloatArray(16)
    private var startTimeNanos = System.nanoTime()

    private val vertexShaderSrc = readRaw(context, rawResId_vertex)
    private val fragmentShaderSrc = readRaw(context, rawResId_fragment)

    private val vertexCoords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    private val vertexBuffer = toFloatBuffer(vertexCoords)
    private val texCoordBuffer = toFloatBuffer(texCoords)

    fun setupGl() {
        program = buildProgram(vertexShaderSrc, fragmentShaderSrc)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        texMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
        filterTypeHandle = GLES20.glGetUniformLocation(program, "uFilterType")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        letterboxHandle = GLES20.glGetUniformLocation(program, "uLetterbox")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    }

    /** SurfaceTexture camera frames attach to; used as the external OES source. */
    fun createInputSurfaceTexture(): SurfaceTexture {
        val st = SurfaceTexture(textureId)
        return st
    }

    fun drawFrame(surfaceTexture: SurfaceTexture) {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, texMatrix, 0)
        GLES20.glUniform1i(filterTypeHandle, filterIndex(currentFilter))
        GLES20.glUniform1f(timeHandle, (System.nanoTime() - startTimeNanos) / 1_000_000_000f)
        GLES20.glUniform1i(letterboxHandle, if (letterboxEnabled) 1 else 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun filterIndex(filter: FilterType): Int = when (filter) {
        FilterType.NONE -> 0
        FilterType.CLASSIC_BW -> 1
        FilterType.CYBERPUNK -> 2
        FilterType.VINTAGE_90S -> 3
        FilterType.CINEMATIC -> 4
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun toFloatBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(array); position(0)
        }

    private fun readRaw(context: Context, resId: Int): String {
        val stream = context.resources.openRawResource(resId)
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    fun release() {
        GLES20.glDeleteProgram(program)
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }
}
