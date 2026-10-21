
package com.jaz3d.app.network

import com.jaz3d.app.storage.ProjectStore
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Development-only LAN bridge for Jaz3D.
 *
 * Routes:
 * GET  /health
 * GET  /character
 * POST /character
 *
 * Character routes require X-Jaz3D-Token.
 */
class LocalBridge(
    private val store: ProjectStore,
    private val token: String,
    private val port: Int = 8765
) {
    private val running = AtomicBoolean(false)
    private val workers = Executors.newCachedThreadPool()

    @Volatile
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return

        workers.execute {
            try {
                ServerSocket(port).use { server ->
                    serverSocket = server

                    while (running.get()) {
                        val client = try {
                            server.accept()
                        } catch (_: Exception) {
                            if (running.get()) continue else break
                        }

                        workers.execute {
                            client.use { socket ->
                                handleClient(socket)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                running.set(false)
            } finally {
                serverSocket = null
            }
        }
    }

    fun stop() {
        running.set(false)

        try {
            serverSocket?.close()
        } catch (_: Exception) {
            // Already closed.
        }

        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 5000

        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        try {
            val requestLine = readLine(input) ?: return
            val requestParts = requestLine.split(" ")

            if (requestParts.size < 2) {
                respond(output, 400, "Bad Request", """{"error":"Invalid request"}""")
                return
            }

            val method = requestParts[0].uppercase()
            val path = requestParts[1].substringBefore("?")
            val headers = mutableMapOf<String, String>()

            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break

                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            if (path == "/health" && method == "GET") {
                respond(
                    output,
                    200,
                    "OK",
                    JSONObject()
                        .put("status", "ok")
                        .put("service", "Jaz3D")
                        .toString()
                )
                return
            }

            if (path != "/character") {
                respond(output, 404, "Not Found", """{"error":"Not found"}""")
                return
            }

            if (token.isBlank() ||
                headers["x-jaz3d-token"] != token
            ) {
                respond(output, 401, "Unauthorized", """{"error":"Unauthorized"}""")
                return
            }

            when (method) {
                "GET" -> {
                    respond(output, 200, "OK", store.exportJson())
                }

                "POST" -> {
                    val contentLength =
                        headers["content-length"]?.toIntOrNull() ?: 0

                    if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
                        respond(
                            output,
                            413,
                            "Payload Too Large",
                            """{"error":"Invalid or oversized request body"}"""
                        )
                        return
                    }

                    val bodyBytes = input.readNBytes(contentLength)
                    if (bodyBytes.size != contentLength) {
                        respond(
                            output,
                            400,
                            "Bad Request",
                            """{"error":"Incomplete request body"}"""
                        )
                        return
                    }

                    val body = String(bodyBytes, StandardCharsets.UTF_8)

                    try {
                        // Validate that the body is JSON before importing.
                        JSONObject(body)
                        store.importJson(body)

                        respond(
                            output,
                            200,
                            "OK",
                            """{"status":"imported"}"""
                        )
                    } catch (_: Exception) {
                        respond(
                            output,
                            400,
                            "Bad Request",
                            """{"error":"Invalid character project JSON"}"""
                        )
                    }
                }

                else -> {
                    respond(
                        output,
                        405,
                        "Method Not Allowed",
                        """{"error":"Method not allowed"}"""
                    )
                }
            }
        } catch (_: Exception) {
            try {
                respond(
                    output,
                    500,
                    "Internal Server Error",
                    """{"error":"Request failed"}"""
                )
            } catch (_: Exception) {
                // Client may already have disconnected.
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        var previous = -1

        while (bytes.size < MAX_HEADER_LINE_BYTES) {
            val current = input.read()
            if (current == -1) {
                if (bytes.isEmpty()) return null
                break
            }

            if (previous == '\r'.code && current == '\n'.code) {
                if (bytes.isNotEmpty()) bytes.removeAt(bytes.lastIndex)
                break
            }

            bytes.add(current.toByte())
            previous = current
        }

        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun respond(
        output: BufferedOutputStream,
        status: Int,
        reason: String,
        body: String
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)

        val header = buildString {
            append("HTTP/1.1 ")
            append(status)
            append(' ')
            append(reason)
            append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ")
            append(bytes.size)
            append("\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Content-Type, X-Jaz3D-Token\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("\r\n")
        }

        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    companion object {
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_HEADER_LINE_BYTES = 8192
    }
}
