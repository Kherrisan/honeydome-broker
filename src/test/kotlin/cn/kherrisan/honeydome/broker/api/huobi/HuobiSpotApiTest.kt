package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.BTC_USDT
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.time.ZoneId
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class HuobiSpotApiTest {

    private lateinit var huobiSpotApi: HuobiSpotApi
    private lateinit var limitOrderId: String

    @BeforeAll
    fun initHuobi() {
        huobiSpotApi = HuobiSpotApi()
        huobiSpotApi.apiKey = System.getenv("huobi.api.key")
        huobiSpotApi.secretKey = System.getenv("huobi.api.secret")
    }

    @Test
    @Order(1)
    fun getCurrencys() = runBlocking {
        val currencys = huobiSpotApi.getCurrencys()
        assert(currencys.size > 3)
        assert(currencys.contains("btc"))
        assert(currencys.contains("eth"))
        assert(currencys.contains("usdt"))
    }

    @Test
    @Order(2)
    fun getSymbols() = runBlocking {
        val symbols = huobiSpotApi.getSymbols()
        assert(symbols.size > 3)
        assert(symbols.contains("btc/usdt"))
    }

    @ObsoleteCoroutinesApi
    @Test
    @Order(3)
    fun getKlines() = runBlocking {
        val now = ZonedDateTime.now()
        val twentyDaysAge = now.minusDays(200)
        val klines = huobiSpotApi.getKlines(BTC_USDT, KlinePeriod.DAY, twentyDaysAge, now)
        println(klines.size)
        assert(klines.isNotEmpty())
        assert(klines.size == 200)
    }

    @Test
    @Order(4)
    fun getSymbolDecimalInfo() = runBlocking {
        val sdi = huobiSpotApi.getSymbolDecimalInfo()
        assert(sdi.size > 3)
        assert(BTC_USDT in sdi)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun subscribeAndUnsubscribeKline() = runBlocking {
        huobiSpotApi.subscribeKline(BTC_USDT, KlinePeriod.DAY) {
            println(it)
        }
        delay(5000)
        huobiSpotApi.unsubscribeKline(BTC_USDT, KlinePeriod.DAY)
        delay(2000)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun subscribeLoadBalancer() = runBlocking {
        huobiSpotApi.marketWs.SUBCSRIPTIONS_THRESHOLD = 2
        huobiSpotApi.subscribeKline(BTC_USDT, KlinePeriod.DAY) {
            println(it)
        }
        huobiSpotApi.subscribeKline("eth/usdt", KlinePeriod.DAY) {
            println(it)
        }
        huobiSpotApi.subscribeKline("xrp/usdt", KlinePeriod.DAY) {
            println(it)
        }
        delay(5000)
        huobiSpotApi.unsubscribeKline("eth/usdt", KlinePeriod.DAY)
        delay(2000)
        huobiSpotApi.unsubscribeKline("xrp/usdt", KlinePeriod.DAY)
        delay(2000)
        huobiSpotApi.unsubscribeKline("btc/usdt", KlinePeriod.DAY)
        delay(2000)
    }

    @ObsoleteCoroutinesApi
    @Test
    fun reconnectSubscribeAfterOffline() = runBlocking {
        huobiSpotApi.subscribeKline("btc/usdt", KlinePeriod.DAY) {
            println(it)
        }
        delay(60000)
        huobiSpotApi.unsubscribeKline("btc/usdt", KlinePeriod.DAY)
    }

    @Test
    @Order(5)
    fun getAccountId() = runBlocking {
        val id = huobiSpotApi.getAccountId()
        println(id)
        assert(huobiSpotApi.accountId != null)
    }

    @Test
    @Order(6)
    fun getBalance() = runBlocking {
        val balance = huobiSpotApi.getBalance()
        println(balance["doge"])
    }

    @Test
    @Order(7)
    fun getOrder() = runBlocking {
        val oid = "255734557251357"
        val order = huobiSpotApi.getOrder(oid, "doge/usdt")
        println(order)
    }

    @Test
    @Order(8)
    fun getOrderMatch() = runBlocking {
        val oid = "255734557251357"
        val matches = huobiSpotApi.getOrderMatch(oid, "doge/usdt")
        println(matches)
    }

    @Test
    @Order(9)
    fun searchOrder() = runBlocking {
        val start = ZonedDateTime.of(2021, 4, 16, 0, 0, 0, 0, ZoneId.systemDefault())
        val end = start.plusDays(1)
        val orders = huobiSpotApi.searchOrders("doge/usdt", start, end)
        println(orders)
    }

    @Test
    @Order(10)
    fun limitBuy() = runBlocking {
    }

    @Test
    @Order(11)
    fun limitSell() = runBlocking {
    }
}
