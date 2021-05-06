package cn.kherrisan.honeydome.broker.common

import java.time.ZonedDateTime

data class KlineRequest(
    val exchange: Exchange,
    val symbol: Symbol,
    val period: KlinePeriod,
    val start: ZonedDateTime,
    val end: ZonedDateTime
)
