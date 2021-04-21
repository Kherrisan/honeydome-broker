package cn.kherrisan.honeydome.broker.common

import java.math.BigDecimal
import java.time.ZonedDateTime

data class BidAsk(
    val exchange: Exchange,
    val symbol: Symbol,
    val time: ZonedDateTime,
    val bid: BigDecimal,
    val bidAmount: BigDecimal,
    val ask: BigDecimal,
    val askAmount: BigDecimal
)
