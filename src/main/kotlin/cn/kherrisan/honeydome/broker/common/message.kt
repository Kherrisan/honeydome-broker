package cn.kherrisan.honeydome.broker.common

import java.time.ZonedDateTime

const val EVENTBUS_REQUEST_KLINE = "eb.req.kline"

data class KlineRequest(
    val exchange: Exchange,
    val symbol: Symbol,
    val period: KlinePeriod,
    val start: ZonedDateTime,
    val end: ZonedDateTime
)
