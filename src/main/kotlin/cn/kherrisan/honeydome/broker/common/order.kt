package cn.kherrisan.honeydome.broker.common

import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.Currency

enum class OrderSide {
    BUY, SELL
}

enum class OrderTypeEnum {
    LIMIT, MARKET
}

enum class OrderState {
    //订单已创建，但还未提交到交易系统
    CREATED,

    //订单已提交到了交易系统，等待撮合
    SUBMITTED,

    //订单已部分成交
    PARTIAL_FILLED,

    //订单已全部成交，此订单终结
    FILLED,

    //订单已撤销，是部分撤销还是全部撤销，看partialfilled的数量
    CANCELED,

    //失败
    FAILED
}

enum class TradeRole {
    TAKER, MAKER
}

data class Order(
    val exchange: Exchange,
    val oid: String,
    val coid: String,
    val symbol: Symbol,
    val side: OrderSide,
    var price: BigDecimal,
    val createTime: ZonedDateTime,
    val type: OrderTypeEnum,
    val backtest: Boolean = false,
    val matches: MutableList<OrderMatch> = mutableListOf()
)

data class OrderMatch(
    val mid: String,
    val role: TradeRole,
    val price: BigDecimal,
    val filledAmount: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: Currency,
    val time: ZonedDateTime
)
