package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.BTC_USDT
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import cn.kherrisan.honeydome.broker.repository.KlineRepository
import cn.kherrisan.honeydome.broker.repository.Mongodb
import cn.kherrisan.honeydome.broker.service.huobi.HuobiSpotFirmbargainService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SpotServiceTest {

    private val huobiSpotService = HuobiSpotFirmbargainService()

    @BeforeAll
    fun setupDB() = runBlocking {
        Mongodb.setup()
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
    fun getKlines() = runBlocking {

    }

    @AfterAll
    fun cleanDB() {
        runBlocking {
            Mongodb.db.dropCollection("kline")
        }
    }
}
