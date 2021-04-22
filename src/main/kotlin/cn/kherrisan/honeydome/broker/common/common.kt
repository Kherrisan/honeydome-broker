package cn.kherrisan.honeydome.broker.common

data class CommonInfo(
    val exchange: Exchange,
    var currencys: List<Currency>,
    var symbols: List<Symbol>,
    var symbolDecimalInfo: MutableMap<Symbol, SymbolDecimalInfo>,
    var currencyDecimalInfo: MutableMap<Currency, Int>
)
