package cn.kherrisan.honeydome.broker.api

import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.common.Currency
import cn.kherrisan.honeydome.broker.defaultCoroutineScope
import cn.kherrisan.honeydome.broker.objSimpleName
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocket
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.lang.RuntimeException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

fun HttpResponse<Buffer>.toJson(): JsonObject {
    return JsonParser.parseString(bodyAsString()).asJsonObject
}

operator fun JsonElement.get(key: String): JsonElement {
    return asJsonObject[key]
}

interface SpotApi {
    suspend fun getCurrencys(): List<Currency>
    suspend fun getSymbols(): List<Symbol>
    suspend fun getKlines(symbol: Symbol, period: KlinePeriod, start: ZonedDateTime, end: ZonedDateTime): List<Kline>
    suspend fun subscribeKline(symbol: Symbol, period: KlinePeriod, handle: suspend (Kline) -> Unit)
    suspend fun unsubscribeKline(symbol: Symbol, period: KlinePeriod)
    suspend fun subscribeBestBidAsk(symbol: Symbol, handle: suspend (BidAsk) -> Unit)
    suspend fun unsubscribeBestBidAsk(symbol: Symbol)
    suspend fun subscribeBalanceUpdate()
    suspend fun unsubscribeBalanceUpdate()
    suspend fun subscribeOrderUpdate()
    suspend fun unsubscribeOrderUpdate()
}

const val DEFAULT_DECIMAL_SCALE = 4

interface DecimalAdaptor {

    fun BigDecimal.toPrice(symbol: Symbol) = price(this, symbol)

    fun price(raw: BigDecimal, symbol: Symbol): BigDecimal =
        raw.setScale(priceMap.getOrDefault(symbol, DEFAULT_DECIMAL_SCALE), RoundingMode.DOWN)

    fun balance(raw: BigDecimal, currency: Currency): BigDecimal =
        raw.setScale(balanceMap.getOrDefault(currency, DEFAULT_DECIMAL_SCALE), RoundingMode.DOWN)

    fun amount(raw: BigDecimal, symbol: Symbol): BigDecimal =
        raw.setScale(amountMap.getOrDefault(symbol, DEFAULT_DECIMAL_SCALE), RoundingMode.DOWN)

    fun volume(raw: BigDecimal, symbol: Symbol): BigDecimal =
        raw.setScale(volumeMap.getOrDefault(symbol, DEFAULT_DECIMAL_SCALE), RoundingMode.DOWN)

    val priceMap: Map<Symbol, Int>
        get() = mapOf()
    val amountMap: Map<Symbol, Int>
        get() = mapOf()
    val volumeMap: Map<Symbol, Int>
        get() = mapOf()
    val balanceMap: Map<Currency, Int>
        get() = mapOf()

    suspend fun getSymbolDecimalInfo(): Map<Symbol, SymbolDecimalInfo>

    suspend fun getCurrencyDecimalInfo(): Map<Currency, Int>
}

interface TextAdaptor {
    fun symbol(raw: String): Symbol = raw.toLowerCase()
    fun currency(raw: String): Currency = raw.toLowerCase()
    fun period(raw: String): KlinePeriod = periodMap.entries.find { it.value == raw }?.key ?: error("")
    fun string(period: KlinePeriod): String = periodMap[period] ?: error("")
    val periodMap: Map<KlinePeriod, String>
}

fun Exchange.spot(): SpotApi {
    return when (this) {
        HUOBI -> HuobiSpotApi()
        else -> throw RuntimeException("Invalid exchange name: $this")
    }
}

interface Websocket {
    suspend fun subscribe(id: String)
    suspend fun unsubscribe(id: String)
    suspend fun sendText(text: String)
    suspend fun sendPong(text: String)
    suspend fun reconnect()
    fun close()
    val subscriptions: MutableList<String>
}

@ObsoleteCoroutinesApi
class BalanceLoaderWebsocket(
    private val websocketFactory: () -> Websocket
) : Websocket {

    private val logger = LoggerFactory.getLogger(BalanceLoaderWebsocket::class.java)
    var SUBCSRIPTIONS_THRESHOLD = 50

    override suspend fun sendText(text: String) {
        mostLighted().sendText(text)
    }

    override suspend fun sendPong(text: String) {
        mostLighted().sendPong(text)
    }

    override suspend fun reconnect() {
        wsQueue.forEach { it.reconnect() }
    }

    override fun close() {
        wsQueue.forEach { it.close() }
        wsQueue.clear()
    }

    override val subscriptions: MutableList<String>
        get() {
            val result = mutableListOf<String>()
            wsQueue.forEach { result += it.subscriptions }
            return result
        }

    private val wsQueue =
        PriorityQueue<Websocket> { ws1, ws2 -> ws1.subscriptions.size - ws2.subscriptions.size }

    init {
        logger.debug("Initialize a new websocket")
        wsQueue += websocketFactory()
    }

    private fun mostLighted(): Websocket {
        if (wsQueue.isEmpty() || wsQueue.first().subscriptions.size >= SUBCSRIPTIONS_THRESHOLD) {
            logger.debug("Initialize a new websocket")
            wsQueue += websocketFactory()
        }
        return wsQueue.first()
    }

    override suspend fun subscribe(id: String) {
        mostLighted().subscribe(id)
    }

    override suspend fun unsubscribe(id: String) {
        wsQueue.find { it.subscriptions.contains(id) }?.unsubscribe(id)
    }
}

@ObsoleteCoroutinesApi
class DefaultWebsocket(
    val url: String,
    val handle: suspend DefaultWebsocket.(Buffer) -> Unit,
    val subscriptionFactory: (String) -> String,
    val unsubscriptionFactory: (String) -> String
) : CoroutineScope by defaultCoroutineScope(), Websocket {

    private var receiveChannel = Channel<Buffer>()
    private var sendMessageChannel = Channel<String>()
    private var sendPongChannel = Channel<String>()
    private val logger = LoggerFactory.getLogger(DefaultWebsocket::class.java)
    private val vertx = VertxHolder.vertx
    private lateinit var ws: WebSocket
    override val subscriptions = mutableListOf<String>()
    private var connectionBinaryBackoffBits = 1
    private var connectionMutex = AtomicBoolean(false)

    @ObsoleteCoroutinesApi
    private val eventLoopContext = newSingleThreadContext("${objSimpleName(this)}Context")

    init {
        launch(eventLoopContext) {
            logger.debug("Start running readChannel loop ${objSimpleName(this)}")
            while (true) {
                try {
                    val buffer = receiveChannel.receive()
                    handle(this@DefaultWebsocket, buffer)
                } catch (e: CancellationException) {
                    logger.debug(e.message)
                }
            }
        }
        launch(eventLoopContext) {
            logger.debug("Start running sendMessageChannel loop ${objSimpleName(this)}")
            while (true) {
                while (!this@DefaultWebsocket::ws.isInitialized || ws.isClosed) {
                    delay(100)
                    continue
                }
                try {
                    val text = sendMessageChannel.receive()
                    logger.debug("Send $text")
                    awaitResult<Void> { ws.writeTextMessage(text, it) }
                } catch (e: CancellationException) {
                    logger.debug(e.message)
                } catch (e: Exception) {
                    logger.error(e.message)
                    reconnect()
                }
            }
        }
        launch(eventLoopContext) {
            logger.debug("Start running sendPongChannel loop ${objSimpleName(this)}")
            while (true) {
                while (!this@DefaultWebsocket::ws.isInitialized || ws.isClosed) {
                    delay(100)
                    continue
                }
                try {
                    val text = sendPongChannel.receive()
                    logger.trace("Send $text")
                    awaitResult<Void> { ws.writePing(Buffer.buffer(text), it) }
                } catch (e: CancellationException) {
                    logger.debug(e.message)
                } catch (e: Exception) {
                    logger.error(e.message)
                    reconnect()
                }
            }
        }
        runBlocking { connect() }
    }

    override suspend fun subscribe(id: String) {
        subscriptions += id
        sendText(subscriptionFactory(id))
    }

    override suspend fun unsubscribe(id: String) {
        subscriptions -= id
        sendText(unsubscriptionFactory(id))
    }

    private suspend fun resubscribeAll() {
        subscriptions.forEach {
            sendText(subscriptionFactory(it))
        }
    }

    suspend fun tryBackoffReconnect() {
        if (connectionMutex.compareAndSet(false, true)) {
            logger.debug("Reconnect $url in ${1 shl connectionBinaryBackoffBits} seconds")
            Timer().schedule(1000L * (1 shl connectionBinaryBackoffBits++)) {
                runBlocking {
                    reconnect()
                }
            }
        }
    }

    override suspend fun reconnect() {
        if (this::ws.isInitialized) {
            ws.close()
        }
        connect()
    }

    suspend fun connect() {
        if (connectionBinaryBackoffBits >= 5) {
            connectionBinaryBackoffBits = 5
        }
        logger.debug("Connect to $url")
        val uri = URI.create(url)
        var port = uri.port
        val options = HttpClientOptions()
        if (uri.scheme == "wss") {
            options.isSsl = true
        }
        if (uri.scheme == "wss" && uri.port == -1) {
            port = 443
        }
        val rp = if (uri.query == null) {
            uri.path
        } else {
            "${uri.path}?${uri.query}"
        }
        try {
            ws = awaitResult { vertx.createHttpClient(options).webSocket(port, uri.host, rp, it) }
            connectionMutex.set(false)
        } catch (e: Exception) {
            logger.error("${e.message}: $url")
            connectionMutex.set(false)
            tryBackoffReconnect()
            return
        } finally {

        }
        ws.handler { receiveChannel.offer(it) }
        ws.closeHandler {

        }
        ws.exceptionHandler {
            runBlocking {
                logger.error("${it.message}: $url")
                tryBackoffReconnect()
            }
        }
        connectionBinaryBackoffBits = 1
        resubscribeAll()
        logger.debug("Connected to $uri")
    }

    override fun close() {
        ws.close()
    }

    override suspend fun sendPong(text: String) {
        sendPongChannel.send(text)
    }

    override suspend fun sendText(text: String) {
        sendMessageChannel.send(text)
    }
}
