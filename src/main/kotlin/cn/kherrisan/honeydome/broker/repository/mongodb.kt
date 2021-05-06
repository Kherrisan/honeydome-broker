package cn.kherrisan.honeydome.broker.repository

import com.github.jershell.kbson.BigDecimalSerializer
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import org.litote.kmongo.serialization.registerSerializer
import org.slf4j.LoggerFactory

open class Repository {
    val db = Mongodb.db
}

object Mongodb {

    private val logger = LoggerFactory.getLogger(this::class.java)
    val db: CoroutineDatabase

    init {
        val connectionStr = "mongodb://honeydome:honeydome_zou970514@localhost"
        val client = KMongo.createClient(connectionStr).coroutine
        db = client.getDatabase("honeydome")
    }

    suspend fun setup() {
        logger.info("初始化 Mongodb 模块")
        registerSerializer(ZonedDateTimeSerializer)
        registerSerializer(BigDecimalSerializer)
        KlineRepository.setup()
    }
}
