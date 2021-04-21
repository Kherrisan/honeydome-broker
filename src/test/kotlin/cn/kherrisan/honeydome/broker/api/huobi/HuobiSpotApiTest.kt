package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.BTC_USDT
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class HuobiSpotApiTest {

    private lateinit var huobiSpotApi: HuobiSpotApi

    @BeforeAll
    fun initHuobi() {
        huobiSpotApi = HuobiSpotApi()
    }

    @Test
    fun getCurrencys() = runBlocking {
        val currencys = huobiSpotApi.getCurrencys()
        assert(currencys.size > 3)
        assert(currencys.contains("btc"))
        assert(currencys.contains("eth"))
        assert(currencys.contains("usdt"))
    }

    @Test
    fun getSymbols() = runBlocking {
        val symbols = huobiSpotApi.getSymbols()
        assert(symbols.size > 3)
        assert(symbols.contains("btc/usdt"))
    }

    @ObsoleteCoroutinesApi
    @Test
    fun getKlines() = runBlocking {
        val now = ZonedDateTime.now()
        val twentyDaysAge = now.minusDays(200)
        val klines = huobiSpotApi.getKlines(BTC_USDT, KlinePeriod.DAY, twentyDaysAge, now)
        println(klines.size)
        assert(klines.isNotEmpty())
        assert(klines.size == 200)
    }

    @Test
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
}
