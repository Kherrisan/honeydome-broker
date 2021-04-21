package cn.kherrisan.honeydome.broker.common

import com.google.gson.JsonElement

typealias Symbol = String
const val DEFAULT_SYMBOL_SPLITTER = "/"
const val BTC_USDT: Symbol = "btc/usdt"
const val EMPTY_SYMBOL: Symbol = "EMPTY"
const val CROSS_SYMBOL: Symbol = "CROSS"

var Symbol.base: Currency
    get() = substringBefore(DEFAULT_SYMBOL_SPLITTER).toLowerCase()
    set(_) {}

var Symbol.quote: Currency
    get() = substringAfter(DEFAULT_SYMBOL_SPLITTER).toLowerCase()
    set(_) {}

fun String.isSymbol(): Boolean {
    return try {
        symbol(this)
        parseSymbol(this)
        true
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
        false
    }
}

fun symbol(base: JsonElement, quote: JsonElement): Symbol = symbol(base.asString, quote.asString)

fun symbol(base: String, quote: String): String = "${base.toLowerCase()}/${quote.toLowerCase()}"

fun symbol(str: String): Symbol {
    assert(str.contains(DEFAULT_SYMBOL_SPLITTER)) { "Invalid symbol format: $str" }
    return str.toLowerCase()
}

fun parseSymbol(str: String, spl: String): Symbol {
    return symbol(str.substringBefore(spl), str.substringAfter(spl)).toLowerCase()
}

fun parseSymbol(str: String, quotes: List<Currency> = COMMON_QUOTES): Symbol {
    val lStr = str.toLowerCase()
    for (quote in quotes) {
        if (lStr.endsWith(quote)) {
            return "${str.removeSuffix(quote)}/${quote}".toLowerCase()
        }
    }
    throw Exception("Invalid base currency in symbol: $lStr")
}
