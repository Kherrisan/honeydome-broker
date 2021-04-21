package cn.kherrisan.honeydome.broker.api

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.awaitResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

class VertxHttp {

    private val client: WebClient

    init {
        val clientOptions = WebClientOptions().setTrustAll(true)
        client = WebClient.create(VertxHolder.vertx, clientOptions)
    }

    private fun urlEncode(params: Map<String, Any>?): String {
        if (params == null || params.isEmpty())
            return ""
        return params.keys.stream()
            .map { key ->
                val encoded = URLEncoder.encode(params[key].toString(), StandardCharsets.UTF_8.toString())
                "$key=$encoded"
            }
            .collect(Collectors.joining("&"))
    }

    suspend fun get(path: String, params: Map<String, String>? = null, headers: Map<String, String>? = null): HttpResponse<Buffer> {
        val url = if (params != null) {
            "$path?${urlEncode(params)}"
        } else {
            path
        }
        val req = client.getAbs(url)
        headers?.forEach { (t, u) -> req.putHeader(t, u) }
        return awaitResult { req.send(it) }
    }

    suspend fun delete(
        path: String,
        params: Map<String, Any>?,
        headers: Map<String, String>?
    ): HttpResponse<Buffer> {
        val req = client.deleteAbs(path)
        headers?.forEach { (t, u) -> req.putHeader(t, u) }
        return if (params != null) {
            awaitResult { req.sendJson(params, it) }
        } else {
            awaitResult { req.send(it) }
        }
    }

    suspend fun post(
        path: String,
        params: Map<String, Any>?,
        headers: Map<String, String>?
    ): HttpResponse<Buffer> {
        val req = client.postAbs(path)
        headers?.forEach { (t, u) -> req.putHeader(t, u) }
        return if (!params.isNullOrEmpty()) {
            awaitResult { req.sendJson(params, it) }
        } else {
            awaitResult { req.send(it) }
        }
    }
}


class VertxHolder {
    companion object {
        val vertx: Vertx = Vertx.vertx()
    }
}
