package cn.kherrisan.honeydome.broker.repository

import cn.kherrisan.honeydome.broker.common.CommonInfo
import cn.kherrisan.honeydome.broker.common.Currency
import cn.kherrisan.honeydome.broker.common.Exchange
import org.litote.kmongo.eq
import org.litote.kmongo.setTo
import org.litote.kmongo.setValue

object CommonInfoRepository : Repository() {

    suspend fun queryCurrencyList(exchange: Exchange): List<Currency> {
        return db.getCollection<CommonInfo>()
            .findOne(CommonInfo::exchange eq exchange)?.currencys ?: emptyList()
    }

    suspend fun saveCurrencys(exchange: Exchange, currencys: List<Currency>) {
        db.getCollection<CommonInfo>()
            .updateOne(CommonInfo::exchange eq exchange, setValue(CommonInfo::currencys, currencys))
    }
}
