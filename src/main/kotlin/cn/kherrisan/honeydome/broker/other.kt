package cn.kherrisan.honeydome.broker

import cn.hutool.core.util.IdUtil
import cn.kherrisan.honeydome.broker.api.VertxHolder
import com.google.gson.*
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun objSimpleName(obj: Any?): String = "${obj?.javaClass?.simpleName}@${obj.hashCode()}"

fun defaultCoroutineScope(): CoroutineScope = CoroutineScope(VertxHolder.vertx.dispatcher())

var incrementId = 0

fun ungzip(byteArray: ByteArray): String {
    val bis = ByteArrayInputStream(byteArray)
    val gis = GZIPInputStream(bis)
    return gis.readAllBytes().toString(StandardCharsets.UTF_8)
}

val DTT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")

fun gmt(): String = Instant.ofEpochSecond(Instant.now().epochSecond).atZone(ZoneId.of("Z")).format(DTT_FORMAT)

fun hmacSHA256Signature(content: String, secret: String): ByteArray {
    val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keySpec)
    return mac.doFinal(content.toByteArray())
}

suspend fun coroutineFixedRateTimer(millis: Long, handler: suspend () -> Unit): Job = GlobalScope.launch {
    while (true) {
        handler()
        delay(millis)
    }
}

fun randomId(): String = IdUtil.randomUUID().substring(0, 9)

class ZonedDatetimeSerializer : JsonSerializer<ZonedDateTime> {
    override fun serialize(src: ZonedDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) {
            JsonNull.INSTANCE
        } else {
            JsonPrimitive(src.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
    }
}

fun gson(): Gson {
    val builder = GsonBuilder()
    builder.registerTypeAdapter(ZonedDateTime::class.java, ZonedDatetimeSerializer())
    return builder.create()
}

fun String.toZonedDateTime(): ZonedDateTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)
