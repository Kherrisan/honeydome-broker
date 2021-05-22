package cn.kherrisan.honeydome.broker.common

typealias Exchange = String

const val HUOBI: Exchange = "huobi"
const val BINANCE: Exchange = "binance"
const val OKEX: Exchange = "okex"

val Exchanges = listOf(HUOBI, BINANCE, OKEX)
