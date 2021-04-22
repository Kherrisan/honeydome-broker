package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.api.*
import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.common.Currency
import cn.kherrisan.honeydome.broker.gmt
import cn.kherrisan.honeydome.broker.hmacSHA256Signature
import cn.kherrisan.honeydome.broker.incrementId
import cn.kherrisan.honeydome.broker.ungzip
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.client.HttpResponse
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.mapOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.sorted
import kotlin.collections.sortedBy
import kotlin.math.min

class HuobiSpotApi : SpotApi, DecimalAdaptor, TextAdaptor {

    private val logger = LoggerFactory.getLogger(HuobiSpotApi::class.java)
    var accountId: String? = null
    lateinit var apiKey: String
    lateinit var secretKey: String

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

    val orderStateMap: Map<String, OrderState> = mapOf(
        "created" to OrderState.CREATED,
        "partial-filled" to OrderState.PARTIAL_FILLED,
        "filled" to OrderState.FILLED,
        "canceled" to OrderState.CANCELED
    )

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

    private fun authUrl(path: String) = "https://api.huobi.pro$path"

    private fun checkResponse(resp: HttpResponse<Buffer>) {
        val obj = JsonParser.parseString(resp.bodyAsString()).asJsonObject
        if (obj.has("status") && obj["status"].asString == "error") {
            error("Unsuccessful request with error response: $obj")
        }
    }

    private fun sortedUrlEncode(params: Map<String, Any>?): String {
        if (params == null || params.isEmpty())
            return ""
        return params.keys.stream()
            .map { key ->
                val encoded = URLEncoder.encode(params[key].toString(), StandardCharsets.UTF_8.toString())
                "$key=$encoded"
            }
            .sorted()
            .collect(Collectors.joining("&"))
    }

//    private fun buildSignedSubpath(
//        path: String,
//        method: String,
//        params: MutableMap<String, Any> = mutableMapOf()
//    ): String {
//        params["AccessKeyId"] = apiKey
//        params["SignatureMethod"] = "HmacSHA256"
//        params["SignatureVersion"] = "2"
//        params["Timestamp"] = gmt()
//        val sb = StringBuilder(1024)
//        sb.append(method.toUpperCase()).append('\n')
//            .append("api.huobi.pro").append('\n')
//            .append(path.removePrefix("https://api.huobi.pro")).append('\n')
//            .append(sortedUrlEncode(params))
//        params["Signature"] =
//            Base64.getEncoder().encodeToString(hmacSHA256Signature(sb.toString(), apiSecret))
//        return "$path?${sortedUrlEncode(params)}"
//    }

    private fun sign(
        method: String,
        url: String,
        params: MutableMap<String, String>
    ) {
        params["AccessKeyId"] = apiKey
        params["SignatureMethod"] = "HmacSHA256"
        params["SignatureVersion"] = "2"
        params["Timestamp"] = gmt()
        val sb = StringBuilder(1024)
        sb.append(method.toUpperCase()).append('\n')
            .append("api.huobi.pro").append('\n')
            .append(url.removePrefix("https://api.huobi.pro")).append('\n')
            .append(sortedUrlEncode(params))
        params["Signature"] =
            Base64.getEncoder().encodeToString(hmacSHA256Signature(sb.toString(), secretKey))
    }

    suspend fun getAccountId(): String {
        val params = mutableMapOf<String, String>()
        sign("GET", authUrl("/v1/account/accounts"), params)
        val resp = http.get(authUrl("/v1/account/accounts"), params)
        val id = resp.toJson()["data"].asJsonArray.map { it.asJsonObject }
            .filter { it["type"].asString == "spot" }
            .map { it["id"].asLong.toString() }
            .first()
        accountId = id
        return id
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
        map.forEach { (symbol, decimal) ->
            priceMap[symbol] = decimal.pricePrecision
            volumeMap[symbol] = decimal.volumePrecision
            amountMap[symbol] = decimal.amountPrecision
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
        map.forEach { (currency, precision) ->
            balanceMap[currency] = precision
        }
        return map
    }

    override suspend fun getBalance(): Map<Currency, Balance> {
        val params = mutableMapOf<String, String>()
        sign("GET", authUrl("/v1/account/accounts/${accountId ?: getAccountId()}/balance"), params)
        val resp = http.get(authUrl("/v1/account/accounts/${accountId ?: getAccountId()}/balance"), params)
        val balance = mutableMapOf<Currency, Balance>()
        resp.toJson()["data"].asJsonObject["list"].asJsonArray.map { it.asJsonObject }
            .forEach {
                val c = it["currency"].asString
                var b = balance[c]
                if (b == null) {
                    b = Balance()
                    balance[c] = b
                }
                if (it["type"].asString == "trade") {
                    b.free = balance(it["balance"].asString.toBigDecimal(), c)
                } else {
                    b.frozen = balance(it["balance"].asString.toBigDecimal(), c)
                }
            }
        return balance
    }

    private fun orderSide(str: String): OrderSide {
        return if (str.contains("-")) {
            val i = str.indexOf("-")
            OrderSide.valueOf(str.substring(0, i).toUpperCase())
        } else {
            OrderSide.valueOf(str.toUpperCase())
        }
    }

    private fun orderType(str: String): OrderType {
        return if (str.contains("-")) {
            // buy-limit
            val i = str.indexOf("-")
            OrderType.valueOf(str.substring(i + 1).toUpperCase())
        } else {
            // limit or market
            OrderType.valueOf(str.toUpperCase())
        }
    }

    override suspend fun getOrder(id: String, symbol: Symbol): Order {
        val params = mutableMapOf<String, String>()
        sign("GET", authUrl("/v1/order/orders/${id}"), params)
        val resp = http.get(authUrl("/v1/order/orders/${id}"), params)
        checkResponse(resp)
        val it = resp.toJson()["data"].asJsonObject
        return Order(
            HUOBI,
            it["id"].asLong.toString(),
            id,
            symbol,
            orderStateMap[it["state"].asString]!!,
            orderSide(it["type"].asString),
            price(it["price"].asString.toBigDecimal(), symbol),
            amount(it["amount"].asString.toBigDecimal(), symbol),
            Instant.ofEpochMilli(it["created-at"].asLong).atZone(ZoneId.systemDefault()),
            orderType(it["type"].asString)
        )
    }

    override suspend fun getOrderMatch(oid: String, symbol: Symbol): List<OrderMatch> {
        val params = mutableMapOf<String, String>()
        sign("GET", authUrl("/v1/order/orders/$oid/matchresults"), params)
        val resp = http.get(authUrl("/v1/order/orders/$oid/matchresults?${sortedUrlEncode(params)}"))
        checkResponse(resp)
        return resp.toJson()["data"].asJsonArray.map { it.asJsonObject }
            .map {
                val fc = it["fee-currency"].asString
                OrderMatch(
                    it["id"].asString,
                    TradeRole.valueOf(it["role"].asString.toUpperCase()),
                    price(it["price"].asString.toBigDecimal(), symbol),
                    balance(it["filled-amount"].asString.toBigDecimal(), symbol),
                    balance(it["filled-fees"].asString.toBigDecimal(), fc),
                    fc,
                    Instant.ofEpochMilli(it["created-at"].asLong).atZone(ZoneId.systemDefault())
                )
            }
    }

    override suspend fun searchOrders(
        symbol: Symbol,
        start: ZonedDateTime,
        end: ZonedDateTime,
        state: OrderState?
    ): List<Order> {
        val params = mutableMapOf(
            "symbol" to symbol.replace("/", ""),
            "start-time" to start.toInstant().toEpochMilli().toString(),
            "end-time" to end.toInstant().toEpochMilli().toString(),
        )
        if (state == null) {
            params["states"] = orderStateMap.keys.joinToString(",")
        } else {
            params["states"] = orderStateMap.filter { it.value == state }.keys.first()
        }
        sign("GET", authUrl("/v1/order/orders"), params)
        val signedUrl = "/v1/order/orders?${sortedUrlEncode(params)}"
        val resp = http.get(authUrl(signedUrl))
        checkResponse(resp)
        return resp.toJson()["data"].asJsonArray.map { it.asJsonObject }
            .map {
                Order(
                    HUOBI,
                    it["id"].asLong.toString(),
                    it["client-order-id"].asString,
                    symbol,
                    orderStateMap[it["state"].asString]!!,
                    orderSide(it["type"].asString),
                    price(it["price"].asString.toBigDecimal(), symbol),
                    amount(it["amount"].asString.toBigDecimal(), symbol),
                    Instant.ofEpochMilli(it["created-at"].asLong).atZone(ZoneId.systemDefault()),
                    orderType(it["type"].asString)
                )
            }
    }

    override suspend fun cancelOrder(oid: String, symbol: Symbol) {
        val params = mutableMapOf<String, String>()
        sign("POST", "/v1/order/orders/${oid}/submitcancel", params)
        val resp = http.post(authUrl("/v1/order/orders/${oid}/submitcancel?${sortedUrlEncode(params)}"))
        checkResponse(resp)
    }

    override suspend fun limitBuy(symbol: Symbol, amount: BigDecimal, price: BigDecimal): String {
        return createOrder(symbol, OrderType.LIMIT, OrderSide.BUY, price, amount)
    }

    override suspend fun limitSell(symbol: Symbol, amount: BigDecimal, price: BigDecimal): String {
        return createOrder(symbol, OrderType.LIMIT, OrderSide.SELL, price, amount)
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String {
        return createOrder(symbol, OrderType.MARKET, OrderSide.BUY, null, amount)
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String {
        return createOrder(symbol, OrderType.MARKET, OrderSide.SELL, null, amount)
    }

    private suspend fun createOrder(
        symbol: Symbol,
        type: OrderType,
        side: OrderSide,
        price: BigDecimal?,
        amount: BigDecimal
    ): String {
        var params = mutableMapOf<String, String>()
        sign("POST", "/v1/order/orders/place", params)
        val signedUrl = "/v1/order/orders/place?${sortedUrlEncode(params)}"
        params = mutableMapOf(
            "account-id" to (accountId ?: getAccountId()),
            "symbol" to symbol.replace("/", ""),
            "type" to "${side.toString().toLowerCase()}-${type.toString().toLowerCase()}",
            "amount" to amount.toString(),
            "source" to "api" // 现货交易填写“api”，杠杆交易填写“margin-api”
        )
        // 限价
        if (type == OrderType.LIMIT) {
            params["price"] = price.toString()
        }
        val resp = http.post(authUrl(signedUrl), params)
        checkResponse(resp)
        return resp.toJson()["data"].asString
    }
}
