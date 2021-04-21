package cn.kherrisan.honeydome.broker.common

import java.math.BigDecimal

data class Balance(
    var free: BigDecimal = BigDecimal.ZERO,
    var frozen: BigDecimal = BigDecimal.ZERO
)
