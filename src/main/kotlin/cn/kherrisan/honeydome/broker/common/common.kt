package cn.kherrisan.honeydome.broker.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommonInfo(
    @SerialName("_id") val exchange: Exchange,
    var currencys: List<Currency>,
    var symbols: List<Symbol>,
    var symbolDecimalInfo: MutableMap<Symbol, SymbolDecimalInfo>,
    var currencyDecimalInfo: MutableMap<Currency, Int>
)
