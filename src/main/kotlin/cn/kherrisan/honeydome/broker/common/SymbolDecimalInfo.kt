package cn.kherrisan.honeydome.broker.common

import kotlinx.serialization.Serializable

@Serializable
data class SymbolDecimalInfo(
    val pricePrecision: Int,
    val amountPrecision: Int,
    val volumePrecision: Int
)
