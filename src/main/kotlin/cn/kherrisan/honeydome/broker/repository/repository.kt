package cn.kherrisan.honeydome.broker.repository

import cn.kherrisan.honeydome.broker.common.*
import com.google.gson.Gson
import com.mongodb.MongoBulkWriteException
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.IndexOptions
import org.litote.kmongo.eq
import org.litote.kmongo.gte
import org.litote.kmongo.insertOne
import org.litote.kmongo.lt
import java.time.ZonedDateTime

object CommonInfoRepository : Repository() {
    suspend fun queryCommonInfo(exchange: Exchange): CommonInfo? {
        return db.getCollection<CommonInfo>()
            .findOne(CommonInfo::exchange eq exchange)
    }

    suspend fun save(info: CommonInfo) {
        db.getCollection<CommonInfo>()
            .save(info)
    }
}

object KlineRepository : Repository() {
    suspend fun setup() {
        if (db.listCollectionNames().contains("kline")) {
            return
        }
        var indexes = mapOf("exchange" to 1, "symbol" to 1, "period" to 1, "time" to 1)
        val options = IndexOptions()
        options.unique(true)
        db.createCollection("kline")
        val collection = db.getCollection<Kline>()
        collection.createIndex(Gson().toJson(indexes), options)
        indexes = mapOf("start" to 1, "end" to 1)
        collection.createIndex(Gson().toJson(indexes))
    }

    suspend fun queryKline(
        exchange: Exchange,
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {
        return db.getCollection<Kline>()
            .find(
                Kline::exchange eq exchange,
                Kline::symbol eq symbol,
                Kline::period eq period,
                Kline::time gte start,
                Kline::time lt end
            ).toList()
    }

    suspend fun save(klines: List<Kline>) {
        if (klines.isEmpty()) {
            return
        }
        val options = BulkWriteOptions()
        options.ordered(false)
        try {
            db.getCollection<Kline>().bulkWrite(klines.map {
                insertOne(it)
            }, options)
        } catch (_: MongoBulkWriteException) {

        }
    }
}

object BalanceRepository : Repository() {
    suspend fun save(snapshot: BalanceSnapshot) {
        db.getCollection<BalanceSnapshot>()
            .save(snapshot)
    }
}

object OrderRepository : Repository() {

    suspend fun save(order: Order) {
        db.getCollection<Order>()
            .save(order)
    }

    suspend fun queryByCoid(cid: String): Order? {
        return db.getCollection<Order>()
            .findOne(Order::coid eq cid)
    }

    suspend fun queryByOid(oid: String): Order? {
        return db.getCollection<Order>()
            .findOne(Order::oid eq oid)
    }
}
