package com.jaz3d.app.network

import com.jaz3d.app.model.CharacterProject
import com.jaz3d.app.storage.ProjectStore
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

class LocalBridge(
    private val store: ProjectStore,
    private val onProjectReceived: (CharacterProject) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var server: ServerSocket? = null

    @Volatile
    private var running = false

    private val token = ByteArray(24).also {
        SecureRandom().nextBytes(it)
    }.joinToString("") { "%02x".format(it) }

    fun pairingToken(): String = token

    fun start(port: Int = 8765) {
        check(!running) { "Bridge is already running" }

        running = true

        executor.execute {
            try {
                server = ServerSocket(port)

                while (running) {
                    val client = server?.accept() ?: break

                    executor.execute {
                        client.use { handleClient(it) }
                    }
                }
            } catch (_: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false

        try {
            server?.close()
        } catch (_: Exception) {
            // The socket may already be closed.
        }

        server = null
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 5000

        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        try {
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")

            if (parts.size < 2) {
                respond(output, 400, """{"error":"bad request"}""")
                return
            }

            val method = parts[0]
            val path = parts[1]

            val headers = mutableMapOf<String, String>()

            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break

                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator)
                        .trim()
                        .lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }

            if (headers["x-jaz3d-token"] != token) {
                respond(output, 401, """{"error":"unauthorized"}""")
                return
            }

            when {
                method == "GET" && path == "/health" -> {
                    respond(
                        output,
                        200,
                        JSONObject()
                            .put("app", "Jaz3D")
                            .put("status", "ready")
                            .toString()
                    )
                }

                method == "GET" && path == "/character" -> {
                    val project = store.load()

                    if (project == null) {
                        respond(
                            output,
                            404,
                            """{"error":"no saved character"}"""
                        )
                    } else {
                        respond(
                            output,
                            200,
                            store.exportJson(project)
                        )
                    }
                }

                method == "POST" && path == "/character" -> {
                    val length = headers["content-length"]
                        ?.toIntOrNull()

                    if (length == null || length !in 1..2_000_000) {
                        respond(
                            output,
                            413,
                            """{"error":"invalid content length"}"""
                        )
                        return
                    }

                    val bytes = ByteArray(length)
                    var offset = 0

                    while (offset < length) {
                        val count = input.read(
                            bytes,
                            offset,
                            length - offset
                        )

                        if (count <= 0) {
                            respond(
                                output,
                                400,
                                """{"error":"incomplete body"}"""
                            )
                            return
                        }

                        offset += count
                    }

                    val json = String(bytes, Charsets.UTF_8)
                    val project = store.importJson(json)

                    store.save(project)
                    onProjectReceived(project)

                    respond(
                        output,
                        200,
                        """{"status":"imported"}"""
                    )
                }

                else -> {
                    respond(
                        output,
                        404,
                        """{"error":"not found"}"""
                    )
                }
            }
        } catch (_: Exception) {
            try {
                respond(
                    output,
                    400,
                    """{"error":"invalid request"}"""
                )
            } catch (_: Exception) {
                // The client may already have disconnected.
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val result = StringBuilder()

        while (true) {
            val value = input.read()
            if (value == -1) {
                return if (result.isEmpty()) null else result.toString()
            }

            if (value == '\n'.code) break
            if (value != '\r'.code) result.append(value.toChar())

            require(result.length <= 8192) {
                "HTTP header line too long"
            }
        }

        return result.toString()
    }

    private fun respond(
        output: BufferedOutputStream,
        status: Int,
        body: String
    ) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Error"
        }

        val bytes = body.toByteArray(Charsets.UTF_8)

        val header = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }

        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }
}
