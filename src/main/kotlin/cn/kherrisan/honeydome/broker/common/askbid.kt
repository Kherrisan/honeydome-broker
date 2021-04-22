@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)

package cn.kherrisan.honeydome.broker.common

import com.github.jershell.kbson.BigDecimalSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.math.BigDecimal
import java.time.ZonedDateTime

@Serializable
data class BidAsk(
    val exchange: Exchange,
    val symbol: Symbol,
    val time: ZonedDateTime,
    val bid: BigDecimal,
    val bidAmount: BigDecimal,
    val ask: BigDecimal,
    val askAmount: BigDecimal
)
