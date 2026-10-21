
package com.jaz3d.app.network

import com.jaz3d.app.storage.ProjectStore
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local network bridge for exchanging Jaz3D character project JSON.
 *
 * Endpoints:
 * GET  /health      Public health check
 * GET  /character   Read the current character (token required)
 * POST /character   Import a character project (token required)
 *
 * Security note:
 * This implementation uses HTTP rather than HTTPS. Use only on a
 * trusted private network. Do not expose this server to the internet.
 */
class LocalBridge(
    private val store: ProjectStore,
    private val port: Int = DEFAULT_PORT
) : AutoCloseable {

    private val random = SecureRandom()
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)

    @Volatile
    private var server: HttpServer? = null

    @Volatile
    private var token: String = generateToken()

    /**
     * Starts the HTTP server and returns its pairing token.
     *
     * Calling start() when already running returns the existing token.
     * After stop(), create a new LocalBridge instance before restarting.
     */
    @Synchronized
    fun start(): String {
        if (running.get()) {
            return token
        }

        check(!executor.isShutdown) {
            "This LocalBridge has been stopped permanently. Create a new instance."
        }

        val newServer = HttpServer.create(
            InetSocketAddress("0.0.0.0", port),
            0
        )

        newServer.executor = executor
        newServer.createContext("/") { exchange ->
            handleRequest(exchange)
        }

        try {
            newServer.start()
            server = newServer
            running.set(true)
            return token
        } catch (exception: Exception) {
            newServer.stop(0)
            throw exception
        }
    }

    /**
     * Stops the server and invalidates its pairing token.
     */
    @Synchronized
    fun stop() {
        if (!running.compareAndSet(true, false)) {
            if (!executor.isShutdown) {
                executor.shutdownNow()
            }
            return
        }

        server?.stop(0)
        server = null

        token = generateToken()
        executor.shutdownNow()
    }

    override fun close() {
        stop()
    }

    /**
     * Returns the current pairing token.
     * Share it only with a trusted client on your local network.
     */
    fun pairingToken(): String = token

    private fun handleRequest(exchange: HttpExchange) {
        try {
            val method = exchange.requestMethod.uppercase()
            val path = exchange.requestURI.path

            // Public connectivity check. Does not reveal the pairing token.
            if (method == "GET" && path == "/health") {
                respond(
                    exchange,
                    200,
                    JSONObject()
                        .put("app", "Jaz3D")
                        .put("status", "ok")
                        .put("apiVersion", 1)
                        .toString()
                )
                return
            }

            // Reject all requests other than the documented endpoints.
            if (
                !(
                    (method == "GET" && path == "/character") ||
                    (method == "POST" && path == "/character")
                )
            ) {
                respond(
                    exchange,
                    404,
                    errorJson("Not found")
                )
                return
            }

            if (!isAuthorized(exchange)) {
                respond(
                    exchange,
                    401,
                    errorJson("Unauthorized")
                )
                return
            }

            when (method) {
                "GET" -> handleGetCharacter(exchange)
                "POST" -> handlePostCharacter(exchange)
                else -> respond(
                    exchange,
                    405,
                    errorJson("Method not allowed")
                )
            }
        } catch (_: PayloadTooLargeException) {
            respondSafely(
                exchange,
                413,
                errorJson("Request body exceeds the 2 MiB limit")
            )
        } catch (exception: IllegalArgumentException) {
            respondSafely(
                exchange,
                400,
                errorJson(exception.message ?: "Invalid request")
            )
        } catch (_: Exception) {
            respondSafely(
                exchange,
                500,
                errorJson("Internal server error")
            )
        } finally {
            exchange.close()
        }
    }

    private fun handleGetCharacter(exchange: HttpExchange) {
        val json = store.exportJson()

        respond(
            exchange,
            200,
            json
        )
    }

    private fun handlePostCharacter(exchange: HttpExchange) {
        val contentType = exchange.requestHeaders
            .getFirst("Content-Type")
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()

        if (contentType != null && contentType != "application/json") {
            respond(
                exchange,
                415,
                errorJson("Content-Type must be application/json")
            )
            return
        }

        val body = exchange.requestBody.use { input ->
            val bytes = input.readNBytes(MAX_BODY_BYTES + 1)

            if (bytes.size > MAX_BODY_BYTES) {
                throw PayloadTooLargeException()
            }

            bytes.toString(Charsets.UTF_8)
        }

        if (body.isBlank()) {
            throw IllegalArgumentException("Request body is empty")
        }

        // Validate JSON before passing it to the project store.
        try {
            JSONObject(body)
        } catch (_: Exception) {
            throw IllegalArgumentException("Request body must be a JSON object")
        }

        store.importJson(body)

        respond(
            exchange,
            200,
            JSONObject()
                .put("status", "saved")
                .toString()
        )
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val suppliedToken = exchange.requestHeaders
            .getFirst(TOKEN_HEADER)
            ?: return false

        val expected = token.toByteArray(Charsets.UTF_8)
        val supplied = suppliedToken.toByteArray(Charsets.UTF_8)

        return MessageDigest.isEqual(expected, supplied)
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.set(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        exchange.responseHeaders.set(
            "Cache-Control",
            "no-store"
        )
        exchange.responseHeaders.set(
            "X-Content-Type-Options",
            "nosniff"
        )

        exchange.sendResponseHeaders(
            status,
            bytes.size.toLong()
        )

        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    private fun respondSafely(
        exchange: HttpExchange,
        status: Int,
        body: String
    ) {
        try {
            respond(exchange, status, body)
        } catch (_: IOException) {
            // The client may have disconnected before receiving the response.
        }
    }

    private fun errorJson(message: String): String {
        return JSONObject()
            .put("error", message)
            .toString()
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)

        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private class PayloadTooLargeException : Exception()

    companion object {
        const val DEFAULT_PORT = 8765

        private const val TOKEN_HEADER = "X-Jaz3D-Token"
        private const val TOKEN_BYTES = 24
        private const val MAX_BODY_BYTES = 2 * 1024 * 1024
    }
}
