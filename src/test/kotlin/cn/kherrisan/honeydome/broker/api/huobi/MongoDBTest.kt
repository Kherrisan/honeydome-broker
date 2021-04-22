package cn.kherrisan.honeydome.broker.api.huobi

import cn.kherrisan.honeydome.broker.common.Kline
import cn.kherrisan.honeydome.broker.repository.Mongodb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MongoDBTest {

    @Test
    fun testSetup() = runBlocking {
        Mongodb.setup()
        assert(Mongodb.db.listCollectionNames().contains("kline"))
        val collection = Mongodb.db.getCollection<Kline>()
    }

}
