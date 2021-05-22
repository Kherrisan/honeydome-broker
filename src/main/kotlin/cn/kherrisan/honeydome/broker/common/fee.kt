@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)
package cn.kherrisan.honeydome.broker.common

import com.github.jershell.kbson.BigDecimalSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.math.BigDecimal

@Serializable
data class Fee(
    val taker: BigDecimal,
    val maker: BigDecimal
)
