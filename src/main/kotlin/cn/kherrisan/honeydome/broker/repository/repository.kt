package cn.kherrisan.honeydome.broker.repository

import cn.kherrisan.honeydome.broker.common.Exchange
import cn.kherrisan.honeydome.broker.common.Kline
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import cn.kherrisan.honeydome.broker.common.Symbol
import com.mongodb.client.model.BulkWriteOptions
import org.litote.kmongo.insertOne
import java.time.ZonedDateTime

object KlineRepository : Repository {
    suspend fun queryKline(
        exchange: Exchange,
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {

    }

    suspend fun save(klines: List<Kline>) {
        val options = BulkWriteOptions()

        db.getCollection<Kline>().bulkWrite(klines.map {
            insertOne(it)
        },)
    }
}
