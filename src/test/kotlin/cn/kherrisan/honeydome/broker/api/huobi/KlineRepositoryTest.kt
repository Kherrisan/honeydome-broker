package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.BTC_USDT
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import cn.kherrisan.honeydome.broker.repository.KlineRepository
import cn.kherrisan.honeydome.broker.repository.Mongodb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.*
import java.time.ZonedDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation::class)
class KlineRepositoryTest {

    private val huobiSpotApi = HuobiSpotApi()
    private val db = Mongodb.db

    @BeforeAll
    fun setupDB() = runBlocking {
        Mongodb.setup()
    }

    @Test
    @Order(1)
    fun save() = runBlocking {
        val end = ZonedDateTime.now()
        val klines = huobiSpotApi.getKlines(BTC_USDT, KlinePeriod.DAY, end.minusDays(100), end).toMutableList()
        if (klines.last().time.plusSeconds(KlinePeriod.DAY.seconds.toLong()) > end) {
            klines.removeLast()
        }
        KlineRepository.save(klines)
    }

    @Test
    @Order(2)
    fun queryKlines() = runBlocking {
        val end = ZonedDateTime.now()
        val klines = KlineRepository.queryKline(HUOBI, BTC_USDT, KlinePeriod.DAY, end.minusDays(21), end)
        println(klines.size)
        assert(klines.size == 21 || klines.size == 20)
    }
}
