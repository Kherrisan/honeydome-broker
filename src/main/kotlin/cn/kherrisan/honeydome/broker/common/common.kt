package cn.kherrisan.honeydome.broker.common

data class CommonInfo(
    val exchange: Exchange,
    val currencys: List<Currency>,
    val symbolDecimalInfo: MutableMap<Symbol, SymbolDecimalInfo>,
    val currencyDecimalInfo: MutableMap<Currency, Int>
)
