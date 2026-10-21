package com.jaz3d.app.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.jaz3d.app.model.CharacterProject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class CharacterRenderer : GLSurfaceView.Renderer {

    @Volatile
    var character = CharacterProject()

    @Volatile
    var rotationY = 0f

    private var program = 0
    private var positionHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)

    private lateinit var vertices: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var indexCount = 0

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?
    ) {
        GLES20.glClearColor(0.055f, 0.067f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShader = """
            uniform mat4 uMVP;
            attribute vec3 aPosition;
            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        program = GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, compileShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShader
            ))
            GLES20.glAttachShader(p, compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShader
            ))
            GLES20.glLinkProgram(p)
        }

        positionHandle =
            GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle =
            GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle =
            GLES20.glGetUniformLocation(program, "uColor")

        createSphere()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(
            projection, 0, 40f, ratio, 0.1f, 100f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT or
                GLES20.GL_DEPTH_BUFFER_BIT
        )

        GLES20.glUseProgram(program)

        Matrix.setLookAtM(
            view, 0,
            0f, 0f, 7.0f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)

        val c = character
        val skin = 0.68f + c.skinTone * 0.25f

        val head = MorphScale(c.headSize, 0.85f, 1.2f)
        val build = MorphScale(c.build, 0.8f, 1.25f)
        val shoulders = MorphScale(c.shoulderWidth, 0.75f, 1.3f)
        val hips = MorphScale(c.hipWidth, 0.75f, 1.3f)
        val torso = MorphScale(c.torsoLength, 0.85f, 1.15f)
        val height = MorphScale(c.height, 0.85f, 1.15f)

        Matrix.setIdentityM(model, 0)
        Matrix.scaleM(model, 0, 1f, height, 1f)
        Matrix.translateM(model, 0, 0f, -0.12f, 0f)

        drawEllipsoid(
            0f, 0.15f, 0f,
            0.50f * build, 0.72f * torso, 0.28f,
            skin, skin * 0.88f, skin * 0.82f
        )

        drawEllipsoid(
            0f, 0.82f, 0f,
            0.43f * shoulders, 0.22f, 0.24f,
            skin, skin * 0.88f, skin * 0.82f
        )

        drawEllipsoid(
            0f, 1.32f, 0f,
            0.22f * head, 0.29f * head, 0.22f * head,
            skin, skin * 0.88f, skin * 0.82f
        )

        drawEllipsoid(
            0f, -0.45f, 0f,
            0.36f * hips, 0.28f, 0.24f,
            skin, skin * 0.88f, skin * 0.82f
        )

        for (side in listOf(-1f, 1f)) {
            drawEllipsoid(
                side * 0.55f * shoulders,
                0.48f, 0f,
                0.13f, 0.38f * build, 0.14f,
                skin, skin * 0.88f, skin * 0.82f
            )

            drawEllipsoid(
                side * 0.55f * shoulders,
                0.02f, 0f,
                0.105f, 0.34f, 0.11f,
                skin, skin * 0.88f, skin * 0.82f
            )

            drawEllipsoid(
                side * 0.20f * hips,
                -0.91f, 0f,
                0.16f * build, 0.42f, 0.17f,
                skin, skin * 0.88f, skin * 0.82f
            )

            drawEllipsoid(
                side * 0.20f * hips,
                -1.34f, 0.04f,
                0.13f, 0.36f, 0.13f,
                skin, skin * 0.88f, skin * 0.82f
            )

            drawEllipsoid(
                side * 0.20f * hips,
                -1.65f, 0.10f,
                0.14f, 0.09f, 0.24f,
                skin, skin * 0.88f, skin * 0.82f
            )
        }

        // Draw a simple ground reference.
        drawEllipsoid(
            0f, -1.85f, 0f,
            0.8f, 0.025f, 0.35f,
            0.16f, 0.20f, 0.28f
        )
    }

    private fun drawEllipsoid(
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        r: Float,
        g: Float,
        b: Float
    ) {
        val local = FloatArray(16)
        val rotated = FloatArray(16)

        Matrix.setIdentityM(local, 0)
        Matrix.translateM(local, 0, x, y, z)
        Matrix.rotateM(local, 0, rotationY, 0f, 1f, 0f)
        Matrix.scaleM(local, 0, sx, sy, sz)

        Matrix.multiplyMM(rotated, 0, model, 0, local, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, rotated, 0)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4f(
            colorHandle, r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f
        )

        vertices.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT,
            false, 12, vertices
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        indices.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            indices
        )
    }

    private fun createSphere() {
        val latitudes = 16
        val longitudes = 24
        val points = ArrayList<Float>()
        val faces = ArrayList<Short>()

        for (lat in 0..latitudes) {
            val theta = Math.PI * lat / latitudes
            for (lon in 0..longitudes) {
                val phi = 2.0 * Math.PI * lon / longitudes
                points.add((sin(theta) * cos(phi)).toFloat())
                points.add(cos(theta).toFloat())
                points.add((sin(theta) * sin(phi)).toFloat())
            }
        }

        for (lat in 0 until latitudes) {
            for (lon in 0 until longitudes) {
                val first = lat * (longitudes + 1) + lon
                val second = first + longitudes + 1

                faces.add(first.toShort())
                faces.add(second.toShort())
                faces.add((first + 1).toShort())

                faces.add(second.toShort())
                faces.add((second + 1).toShort())
                faces.add((first + 1).toShort())
            }
        }

        vertices = ByteBuffer
            .allocateDirect(points.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(points.toFloatArray())
            .apply { position(0) }

        indices = ByteBuffer
            .allocateDirect(faces.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(faces.toShortArray())
            .apply { position(0) }

        indexCount = faces.size
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }

    private fun MorphScale(
        value: Float,
        minimum: Float,
        maximum: Float
    ): Float = minimum + value.coerceIn(0f, 1f) * (maximum - minimum)
}
