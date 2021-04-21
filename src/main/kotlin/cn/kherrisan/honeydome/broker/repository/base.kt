package cn.kherrisan.honeydome.broker.repository

import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

open class Repository {
    val db = Mongodb.db
}

object Mongodb {

    val db: CoroutineDatabase

    init {
        val client = KMongo.createClient().coroutine
        db = client.getDatabase("honeydome-broker")
    }
}
