package cn.kherrisan.honeydome.broker.repository

import com.github.jershell.kbson.BigDecimalSerializer
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import org.litote.kmongo.serialization.registerSerializer

open class Repository() {
    val db = Mongodb.db
}

object Mongodb {

    val db: CoroutineDatabase

    init {
        val client = KMongo.createClient().coroutine
        db = client.getDatabase("honeydome-broker")
    }

    suspend fun setup() {
        registerSerializer(ZonedDateTimeSerializer)
        registerSerializer(BigDecimalSerializer)
        KlineRepository.setup()
    }
}
