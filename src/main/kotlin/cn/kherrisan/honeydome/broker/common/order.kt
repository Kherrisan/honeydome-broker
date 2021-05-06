@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)

package cn.kherrisan.honeydome.broker.common

import com.github.jershell.kbson.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.math.BigDecimal
import java.time.ZonedDateTime

enum class OrderSide {
    BUY, SELL
}

enum class OrderType {
    LIMIT, MARKET
}

enum class OrderState {
    //订单已创建，但还未提交到交易系统
    CREATED,

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

@Serializable
data class Order(
    val exchange: Exchange,
    var oid: String,
    @SerialName("_id") var coid: String,
    val symbol: Symbol,
    val state: OrderState,
    val side: OrderSide,
    var price: BigDecimal,
    val amount: BigDecimal,
    val createTime: ZonedDateTime,
    val type: OrderType,
    val backtest: Boolean = false,
    val matches: MutableList<OrderMatch> = mutableListOf()
)

@Serializable
data class OrderMatch(
    @SerialName("_id") val mid: String,
    val oid: String,
    val role: TradeRole,
    val price: BigDecimal,
    val filledAmount: BigDecimal,
    val fee: BigDecimal,
    val feeCurrency: Currency,
    val time: ZonedDateTime
)
