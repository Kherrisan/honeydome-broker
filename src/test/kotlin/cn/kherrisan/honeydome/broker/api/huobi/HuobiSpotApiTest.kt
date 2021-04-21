package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.BTC
import cn.kherrisan.honeydome.broker.common.BTC_USDT
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import cn.kherrisan.honeydome.broker.common.USDT
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.time.ZonedDateTime
import kotlin.test.AfterTest

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

    @Test
    fun getAccountId() = runBlocking {
        huobiSpotApi.apiKey = System.getenv("huobi.api.key")
        huobiSpotApi.apiSecret = System.getenv("huobi.api.secret")
        assert(huobiSpotApi.accountId == null)
        val id = huobiSpotApi.getAccountId()
        println(id)
        assert(huobiSpotApi.accountId != null)
    }

    @Test
    fun getBalance() = runBlocking {
        val balance = huobiSpotApi.getBalance()
        println(balance["doge"])
    }
}
