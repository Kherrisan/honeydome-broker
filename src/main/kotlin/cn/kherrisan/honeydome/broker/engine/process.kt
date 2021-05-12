@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)

package cn.kherrisan.honeydome.broker.engine

import cn.kherrisan.honeydome.broker.common.Order
import com.github.jershell.kbson.BigDecimalSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.time.ZonedDateTime

@Serializable
data class Process(
    @SerialName("_id") val pid: String,
    val strategy: String,
    val launchTime: ZonedDateTime,
    var terminateTime: ZonedDateTime,
    val backtest: Boolean,
    var args: Map<String, String>,
    val orders: MutableList<Order> = mutableListOf(),
)
