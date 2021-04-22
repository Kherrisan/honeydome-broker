package cn.kherrisan.honeydome.broker.repository

import cn.kherrisan.honeydome.broker.common.Kline
import com.google.gson.Gson
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.IndexOptions
import kotlinx.coroutines.runBlocking
import org.bson.BSONObject
import org.bson.BasicBSONObject
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

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
        KlineRepository.setup()
    }
}
