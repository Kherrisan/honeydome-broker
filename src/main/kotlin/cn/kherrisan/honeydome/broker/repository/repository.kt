package cn.kherrisan.honeydome.broker.repository

import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.gson
import com.google.gson.Gson
import com.mongodb.MongoBulkWriteException
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.UpdateOptions
import org.bson.BsonDocument
import org.litote.kmongo.*
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
            .save(snapshot.ignoreZeroBalance())
    }

    suspend fun queryByExchangeAndDatetimeRange(
        exchange: Exchange,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<BalanceSnapshot> =
        db.getCollection<BalanceSnapshot>()
            .find(
                BalanceSnapshot::exchange eq exchange,
                BalanceSnapshot::time gte start,
                BalanceSnapshot::time lte end
            )
            .toList()
}

object OrderRepository : Repository() {

    suspend fun save(order: Order) {
        if (order.coid.isEmpty()) {
            var target = gson().toJsonTree(order)
            target = target.asJsonObject.remove("coid")
            db.getCollection<Order>()
                .updateOne(Order::oid eq order.oid, BsonDocument.parse(gson().toJson(target)))
        } else {
            db.getCollection<Order>()
                .save(order)
        }
    }

    suspend fun queryByCoid(cid: String): Order? {
        return db.getCollection<Order>()
            .findOne(Order::coid eq cid)
    }

    suspend fun queryByExchangeAndOid(exchange: Exchange, oid: String): Order? {
        return db.getCollection<Order>()
            .findOne(Order::exchange eq exchange, Order::oid eq oid)
    }
}

object OrderMatchTempRepository : Repository() {
    suspend fun save(match: OrderMatch) = db.getCollection<OrderMatch>().save(match)

    suspend fun queryAll(): List<OrderMatch> = db.getCollection<OrderMatch>().find().toList()
    suspend fun delete(match: OrderMatch) = db.getCollection<OrderMatch>().deleteOneById(match.mid)
}
