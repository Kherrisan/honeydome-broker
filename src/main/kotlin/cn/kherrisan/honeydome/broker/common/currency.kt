package cn.kherrisan.honeydome.broker.common

typealias Currency = String

const val BTC: Currency = "btc"
const val ETH: Currency = "eth"
const val USDT: Currency = "usdt"

val COMMON_QUOTES = listOf(BTC, ETH, USDT)

fun currency(string: String): Currency = string.toLowerCase()
