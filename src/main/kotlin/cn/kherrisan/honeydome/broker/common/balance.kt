package cn.kherrisan.honeydome.broker.common

import java.math.BigDecimal
import java.time.ZonedDateTime

data class Balance(
    var free: BigDecimal = BigDecimal.ZERO,
    var frozen: BigDecimal = BigDecimal.ZERO
)

data class BalanceSnapshot(
    val exchange: Exchange,
    val time: ZonedDateTime,
    val balances: Map<Currency, Balance>
)
