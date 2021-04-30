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

fun HttpResponse<Buffer>.toJsonElement(): JsonObject {
    return JsonParser.parseString(bodyAsString()).asJsonObject
}

operator fun JsonElement.get(key: String): JsonElement {
    return asJsonObject[key]
}

interface SpotApi {
    suspend fun getCurrencys(): List<Currency>
    suspend fun getSymbols(): List<Symbol>
    suspend fun getKlines(symbol: Symbol, period: KlinePeriod, start: ZonedDateTime, end: ZonedDateTime): List<Kline>
    suspend fun getBalance(): Map<Currency, Balance>
    suspend fun getOrder(id: String, symbol: Symbol): Order
    suspend fun getOrderMatch(oid: String, symbol: Symbol): List<OrderMatch>
    suspend fun searchOrders(
        symbol: Symbol,
        start: ZonedDateTime,
        end: ZonedDateTime = ZonedDateTime.now(),
        state: OrderState? = null
    ): List<Order>

    suspend fun cancelOrder(oid: String, symbol: Symbol)
    suspend fun limitBuy(symbol: Symbol, amount: BigDecimal, price: BigDecimal): String
    suspend fun limitSell(symbol: Symbol, amount: BigDecimal, price: BigDecimal): String
    suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String
    suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String
    suspend fun subscribeBalanceUpdate(handle: suspend (balances: Pair<Currency, Balance>) -> Unit)
    suspend fun unsubscribeBalanceUpdate()
    suspend fun subscribeOrderUpdate()
    suspend fun unsubscribeOrderUpdate()

    @ObsoleteCoroutinesApi
    suspend fun unsubscribeBestBidAsk(symbol: Symbol)

    @ObsoleteCoroutinesApi
    suspend fun subscribeBestBidAsk(symbol: Symbol, handle: suspend (BidAsk) -> Unit)

    @ObsoleteCoroutinesApi
    suspend fun unsubscribeKline(symbol: Symbol, period: KlinePeriod)

    @ObsoleteCoroutinesApi
    suspend fun subscribeKline(symbol: Symbol, period: KlinePeriod, handle: suspend (Kline) -> Unit)
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

    val priceMap: MutableMap<Symbol, Int>
        get() = mutableMapOf()
    val amountMap: MutableMap<Symbol, Int>
        get() = mutableMapOf()
    val volumeMap: MutableMap<Symbol, Int>
        get() = mutableMapOf()
    val balanceMap: MutableMap<Currency, Int>
        get() = mutableMapOf()

    suspend fun getSymbolDecimalInfo(): Map<Symbol, SymbolDecimalInfo>

    suspend fun getCurrencyDecimalInfo(): Map<Currency, Int>
}

interface TextAdaptor {
    fun symbol(raw: String): Symbol = raw.toLowerCase()
    fun period(raw: String): KlinePeriod = periodMap.entries.find { it.value == raw }?.key ?: error("")
    fun string(period: KlinePeriod): String = periodMap[period] ?: error("")
    val periodMap: Map<KlinePeriod, String>
}
