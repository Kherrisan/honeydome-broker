package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.api.*
import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.incrementId
import cn.kherrisan.honeydome.broker.ungzip
import cn.kherrisan.kommons.set
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.min

class HuobiSpotApi : SpotApi, DecimalAdaptor, TextAdaptor {

    private val logger = LoggerFactory.getLogger(HuobiSpotApi::class.java)

    @ObsoleteCoroutinesApi
    private fun marketWsFactory() = DefaultWebsocket("wss://api.huobi.pro/ws", handle = { buffer ->
        val decoded = ungzip(buffer.bytes)
        logger.trace(decoded)
        val obj = JsonParser.parseString(decoded).asJsonObject
        when {
            obj.has("ping") -> {
                // ping-pong
                sendText(Gson().toJson(mapOf("pong" to obj["ping"].asLong)))
            }
            obj.has("subbed") -> {
                // sub-event
            }
            obj.has("unsubbed") -> {
                // unsub-event
            }
            obj.has("rep") -> {
                val ch = obj["rep"].asString
                val handle = subscriptionHandleMap.remove(ch)
                handle?.invoke(decoded)
            }
            else -> {
                invokeSubscriptionHandle(decoded, obj["ch"].asString)
            }
        }
    }, subscriptionFactory = { id ->
        Gson().toJson(
            mapOf(
                "sub" to id,
                "id" to incrementId++
            )
        )
    }, unsubscriptionFactory = { id ->
        Gson().toJson(
            mapOf(
                "unsub" to id,
                "id" to incrementId++
            )
        )
    })

    private val subscriptionHandleMap = mutableMapOf<String, suspend (String) -> Unit>()

    @ObsoleteCoroutinesApi
    val marketWs = BalanceLoaderWebsocket(this::marketWsFactory)

    private suspend fun invokeSubscriptionHandle(clear: String, ch: String) {
        try {
            val handle = subscriptionHandleMap[ch]
            handle?.invoke(ch)
        } catch (e: Exception) {
            logger.error(e.message)
            logger.error("Error in dispatch: $clear")
            e.printStackTrace()
        }
    }

    private var authPromise = Promise.promise<Unit>()

    @ObsoleteCoroutinesApi
    private var tradingWs = DefaultWebsocket("wss://api.huobi.pro/ws/v2", handle = { buffer ->
        val clear = buffer.bytes.decodeToString()
        logger.trace(clear)
        val obj = JsonParser.parseString(clear).asJsonObject
        val action = obj["action"].asString
        when (action) {
            "ping" -> {
                //ping-pong
                obj["action"] = "pong"
                sendText(Gson().toJson(obj)))
            }
            "req" -> {
                logger.debug(obj)
                if (obj["ch"].asString == "auth" && obj["code"].asInt == 200) {
                    authPromise.complete()
                }
            }
            "sub" -> {
            }
            "unsub" -> {
            }
            "push" -> {
                invokeSubscriptionHandle(clear, obj["ch"].asString)
            }
        }
    })

    override val periodMap: Map<KlinePeriod, String>
        get() = mapOf(
            KlinePeriod.MINUTE to "1min",
            KlinePeriod.QUARTER to "15min",
            KlinePeriod.HOUR to "60min",
            KlinePeriod.DAY to "1day",
            KlinePeriod.WEEK to "1week"
        )

    private val http = VertxHttp()

    private fun publicUrl(path: String) = "https://api.huobi.pro$path"

    private fun checkResponse(resp: HttpResponse<Buffer>) {
        val obj = JsonParser.parseString(resp.bodyAsString()).asJsonObject
        if (obj.has("status") && obj["status"].asString == "error") {
            error("Unsuccessful request with error response: $obj")
        }
    }

    override suspend fun getCurrencys(): List<Currency> {
        val resp = http.get(publicUrl("/v1/common/currencys"))
        checkResponse(resp)
        return resp.toJson()["data"].asJsonArray
            .map { it.asString.toLowerCase() }
            .sorted()
            .map { it.toLowerCase() }
    }

    override suspend fun getSymbols(): List<Symbol> {
        val resp = http.get(publicUrl("/v1/common/symbols"))
        checkResponse(resp)
        return resp.toJson()["data"].asJsonArray
            .map { it.asJsonObject }
            .map {
                it["base-currency"].asString.toLowerCase() +
                    DEFAULT_SYMBOL_SPLITTER +
                    it["quote-currency"].asString.toLowerCase()
            }
            .sortedBy { it.base }
    }

    private fun kline(symbol: Symbol, period: KlinePeriod, it: JsonObject): Kline =
        Kline(
            HUOBI,
            symbol,
            Instant.ofEpochSecond(it["id"].asLong).atZone(ZoneId.systemDefault()),
            price(it["open"].asBigDecimal, symbol),
            price(it["close"].asBigDecimal, symbol),
            price(it["high"].asBigDecimal, symbol),
            price(it["low"].asBigDecimal, symbol),
            volume(it["vol"].asBigDecimal, symbol),
            period
        )

    @ObsoleteCoroutinesApi
    override suspend fun getKlines(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {
        val promise = Promise.promise<List<Kline>>()
        val future = promise.future()
        val id = "market.${symbol.replace("/", "")}.kline.${string(period)}"
        marketWs.sendText(
            Gson().toJson(
                mapOf(
                    "req" to id,
                    "id" to incrementId++,
                    "from" to start.toInstant().epochSecond,
                    "to" to end.toInstant().epochSecond,
                )
            )
        )
        subscriptionHandleMap[id] = { resp ->
            val it = JsonParser.parseString(resp)
            val klines = it["data"].asJsonArray.map {
                it.asJsonObject.run {
                    kline(symbol, period, this)
                }
            }
            promise.complete(klines)
        }
        future.await()
        return future.result()
    }

    override suspend fun getSymbolDecimalInfo(): Map<Symbol, SymbolDecimalInfo> {
        val resp = http.get(publicUrl("/v1/common/symbols"))
        checkResponse(resp)
        val map = mutableMapOf<Symbol, SymbolDecimalInfo>()
        resp.toJson()["data"].asJsonArray
            .map { it.asJsonObject }
            .forEach {
                map[symbol(it["base-currency"], it["quote-currency"])] =
                    SymbolDecimalInfo(
                        it["price-precision"].asInt,
                        it["amount-precision"].asInt,
                        it["value-precision"].asInt
                    )
            }
        return map
    }

    override suspend fun getCurrencyDecimalInfo(): Map<Currency, Int> {
        val resp = http.get(publicUrl("/v1/common/symbols"))
        checkResponse(resp)
        val map = mutableMapOf<Currency, Int>()
        resp.toJson()["data"].asJsonArray
            .map { it.asJsonObject }
            .forEach {
                val currency = it["base-currency"].asString.toLowerCase()
                map[currency] = min(it["amount-precision"].asInt, (map[currency] ?: 20))
            }
        return map
    }

    @ObsoleteCoroutinesApi
    override suspend fun subscribeKline(symbol: Symbol, period: KlinePeriod, handle: suspend (Kline) -> Unit) {
        val id = "market.${symbol.replace("/", "")}.kline.${string(period)}"
        if (subscriptionHandleMap.containsKey(id)) {
            return
        }
        marketWs.subscribe(id)
        subscriptionHandleMap[id] = {
            val tick = JsonParser.parseString(it)["tick"].asJsonObject
            handle(
                kline(symbol, period, tick)
            )
        }
    }

    @ObsoleteCoroutinesApi
    override suspend fun unsubscribeKline(symbol: Symbol, period: KlinePeriod) {
        val id = "market.${symbol.replace("/", "")}.kline.${string(period)}"
        if (!subscriptionHandleMap.containsKey(id)) {
            return
        }
        marketWs.unsubscribe(id)
    }

    @ObsoleteCoroutinesApi
    override suspend fun subscribeBestBidAsk(symbol: Symbol, handle: suspend (BidAsk) -> Unit) {
        val id = "market.${symbol.base}${symbol.quote}.bbo"
        if (subscriptionHandleMap.containsKey(id)) {
            return
        }
        marketWs.subscribe(id)
        subscriptionHandleMap[id] = {
            val tick = JsonParser.parseString(it)["tick"].asJsonObject
            handle(
                BidAsk(
                    HUOBI,
                    symbol,
                    Instant.ofEpochSecond(tick["quoteTime"].asString.toLong()).atZone(ZoneId.systemDefault()),
                    price(tick["bid"].asString.toBigDecimal(), symbol),
                    amount(tick["bidSize"].asString.toBigDecimal(), symbol),
                    price(tick["ask"].asString.toBigDecimal(), symbol),
                    amount(tick["askSize"].asString.toBigDecimal(), symbol)
                )
            )
        }
    }

    @ObsoleteCoroutinesApi
    override suspend fun unsubscribeBestBidAsk(symbol: Symbol) {
        val id = "market.${symbol.base}${symbol.quote}.bbo"
        if (!subscriptionHandleMap.containsKey(id)) {
            return
        }
        marketWs.unsubscribe(id)
    }

    override suspend fun subscribeBalanceUpdate() {
        TODO("subscribeBalanceUpdate")
    }

    override suspend fun unsubscribeBalanceUpdate() {
        TODO("unsubscribeBalanceUpdate")
    }

    override suspend fun subscribeOrderUpdate() {
        TODO("subscribeOrderUpdate")
    }

    override suspend fun unsubscribeOrderUpdate() {
        TODO("unsubscribeOrderUpdate")
    }
}
