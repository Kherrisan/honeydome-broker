package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.repository.KlineRepository
import cn.kherrisan.honeydome.broker.repository.Mongodb
import cn.kherrisan.honeydome.broker.service.huobi.HuobiSpotFirmbargainService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Order
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SpotServiceTest {

    private val huobiSpotService = HuobiSpotFirmbargainService()

    @BeforeAll
    fun setupDB() = runBlocking {
        Mongodb.setup()
    }

    @BeforeAll
    fun setupService() = runBlocking {
        huobiSpotService.setup()
    }

    @Test
    @Order(1)
    fun getTodayKline() = runBlocking {
        val end = ZonedDateTime.now()
        var klines = huobiSpotService.getKline(BTC_USDT, KlinePeriod.DAY, end.minusDays(3), end)
        assert(klines.size == 2)
        klines = KlineRepository.queryKline(HUOBI, BTC_USDT, KlinePeriod.DAY, end.minusDays(4), end.plusDays(1))
        assert(klines.size == 2)
        huobiSpotService.getKline(BTC_USDT, KlinePeriod.DAY, end.minusDays(400), end.minusDays(10))
        klines = KlineRepository.queryKline(HUOBI, BTC_USDT, KlinePeriod.DAY, end.minusDays(100), end.plusDays(1))
        assert(klines.size == 92)
        klines = huobiSpotService.getKline(BTC_USDT, KlinePeriod.DAY, end.minusDays(600), end.minusDays(1))
        println(klines.size)
//        klines = KlineRepository.queryKline(HUOBI, BTC_USDT, KlinePeriod.DAY, end.minusDays(600), end.plusDays(1))
        assert(klines.size == 599)
    }

    @Test
    @Order(2)
    @InternalCoroutinesApi
    fun getKlineChannel() = runBlocking {
        val end = ZonedDateTime.now()
        var klineList = mutableListOf<Kline>()
        huobiSpotService.getKlineChannel(BTC_USDT, KlinePeriod.DAY, end.minusDays(50), end).consumeEach {
            println(it)
            klineList.add(it)
        }
        println(klineList.size)
        klineList.clear()
        delay(3000)
        huobiSpotService.getKlineChannel(BTC_USDT, KlinePeriod.DAY, end.minusDays(100), end.minusDays(40)).consumeEach {
            println(it)
            klineList.add(it)
        }
        println(klineList.size)
        klineList.clear()
        delay(3000)
        huobiSpotService.getKlineChannel(BTC_USDT, KlinePeriod.DAY, end.minusDays(400), end.minusDays(20)).consumeEach {
            println(it)
            klineList.add(it)
        }
        println(klineList.size)
    }

    @Test
    @Order(3)
    fun getBalance() = runBlocking {
        delay(3000)
        println(huobiSpotService.getBalance())
        //做一次资金划转
        delay(10000)
        println(huobiSpotService.getBalance())
    }

    @AfterAll
    fun cleanDB() {
        runBlocking {
            Mongodb.db.dropCollection<Kline>()
            Mongodb.db.dropCollection<BalanceSnapshot>()
        }
    }
}
