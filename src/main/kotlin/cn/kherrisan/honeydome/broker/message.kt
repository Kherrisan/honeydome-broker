package cn.kherrisan.honeydome.broker

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.awaitResult
import org.slf4j.LoggerFactory

object Bark {

    private val client: WebClient = WebClient.create(Vertx.vertx())
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun send(msg: String) {
        val body = mapOf(
            "device_key" to "wseihimsylncyvfu",
            "title" to "honeydome",
            "body" to msg
        )
        val request = client.postAbs("https://bark.kherrisan.cn/push")
        awaitResult<HttpResponse<Buffer>> { request.sendJson(body, it) }
        logger.debug("Send message to bark: $msg")
    }
}
