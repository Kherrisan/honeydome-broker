package cn.kherrisan.honeydome.broker

import cn.kherrisan.honeydome.broker.api.VertxHolder
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

fun objSimpleName(obj: Any?): String = "${obj?.javaClass?.simpleName}@${obj.hashCode()}"

fun defaultCoroutineScope(): CoroutineScope = CoroutineScope(VertxHolder.vertx.dispatcher() + SupervisorJob())

var incrementId = 0

fun ungzip(byteArray: ByteArray): String {
    val bis = ByteArrayInputStream(byteArray)
    val gis = GZIPInputStream(bis)
    return gis.readAllBytes().toString(StandardCharsets.UTF_8)
}
