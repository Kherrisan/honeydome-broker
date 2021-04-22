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
)

enum class KlinePeriod {
    MINUTE,
    QUARTER,
    HOUR,
    DAY,
    WEEK;

    val seconds: Long
        get() {
            return when (this) {
                MINUTE -> 60
                QUARTER -> MINUTE.seconds * 15
                HOUR -> QUARTER.seconds * 4
                DAY -> HOUR.seconds * 24
                WEEK -> DAY.seconds * 7
            }
        }
}
