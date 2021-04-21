package cn.kherrisan.honeydome.broker.common

data class SymbolDecimalInfo(
    val pricePrecision: Int,
    val amountPrecision: Int,
    val volumePrecision: Int
)
