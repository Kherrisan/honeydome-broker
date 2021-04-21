package cn.kherrisan.honeydome.broker.common

import java.math.BigDecimal
import java.time.ZonedDateTime

data class Kline(
    var exchange: Exchange,
    var symbol: Symbol,
    var time: ZonedDateTime,
    var open: BigDecimal,
    var close: BigDecimal,
    var high: BigDecimal,
    var low: BigDecimal,
    var volume: BigDecimal,
    var period: KlinePeriod,
    var kid: String = ""
)

enum class KlinePeriod {
    MINUTE,
    QUARTER,
    HOUR,
    DAY,
    WEEK
}
