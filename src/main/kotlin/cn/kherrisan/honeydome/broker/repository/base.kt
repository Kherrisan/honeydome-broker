package cn.kherrisan.honeydome.broker.repository

import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

open class Repository {
    protected val client = KMongo.createClient().coroutine
    protected val db = client.getDatabase("honeydome-broker")
}
