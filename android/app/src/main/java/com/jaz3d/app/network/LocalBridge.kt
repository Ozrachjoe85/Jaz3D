package com.jaz3d.app.network

import com.jaz3d.app.model.CharacterProject
import com.jaz3d.app.storage.ProjectStore
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.Executors

class LocalBridge(
    private val store: ProjectStore,
    private val port: Int = 8765
) {
    private var server: HttpServer? = null
    private val executor = Executors.newCachedThreadPool()
    private val random = SecureRandom()

    @Volatile
    private var token: String = generateToken()

    @Synchronized
    fun start(): String {
        if (server != null) return token

        val httpServer = HttpServer.create(
            InetSocketAddress("0.0.0.0", port),
            0
        )

        httpServer.executor = executor

        httpServer.createContext("/") { exchange ->
            handleRequest(exchange)
        }

        httpServer.start()
        server = httpServer

        return token
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        executor.shutdownNow()
        token = generateToken()
    }

    fun pairingToken(): String = token

    private fun handleRequest(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod.uppercase()

            if (method == "GET" && path == "/health") {
                respond(
                    exchange,
                    200,
                    JSONObject()
                        .put("app", "Jaz3D")
                        .put("status", "ok")
                        .toString()
                )
                return
            }

            val suppliedToken = exchange.requestHeaders
                .getFirst("X-Jaz3D-Token")

            if (suppliedToken == null || suppliedToken != token) {
                respond(exchange, 401, """{"error":"Unauthorized"}""")
                return
            }

            when {
                method == "GET" && path == "/character" -> {
                    val json = store.exportJson()
                    respond(exchange, 200, json)
                }

                method == "POST" && path == "/character" -> {
                    val body = exchange.requestBody
                        .use { input ->
                            val bytes = input.readNBytes(MAX_BODY_BYTES + 1)
                            if (bytes.size > MAX_BODY_BYTES) {
                                throw PayloadTooLargeException()
                            }
                            bytes.toString(Charsets.UTF_8)
                        }

                    JSONObject(body)
                    store.importJson(body)

                    respond(
                        exchange,
                        200,
                        """{"status":"saved"}"""
                    )
                }

                else -> respond(
                    exchange,
                    404,
                    """{"error":"Not found"}"""
                )
            }
        } catch (_: PayloadTooLargeException) {
            respondSafely(
                exchange,
                413,
                """{"error":"Payload too large"}"""
            )
        } catch (e: IllegalArgumentException) {
            respondSafely(
                exchange,
                400,
                JSONObject()
                    .put("error", e.message ?: "Invalid request")
                    .toString()
            )
        } catch (_: Exception) {
            respondSafely(
                exchange,
                500,
                """{"error":"Internal server error"}"""
            )
        } finally {
            exchange.close()
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.set(
            "Content-Type",
            "application/json; charset
