@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)

package cn.kherrisan.honeydome.broker.common

import com.github.jershell.kbson.BigDecimalSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.math.BigDecimal
import java.time.ZonedDateTime

@Serializable
data class Balance(
    var free: BigDecimal = BigDecimal.ZERO,
    var frozen: BigDecimal = BigDecimal.ZERO
)

@Serializable
data class BalanceSnapshot(
    val exchange: Exchange,
    val time: ZonedDateTime,
    var balances: Map<Currency, Balance>
) {
    fun ignoreZeroBalance(): BalanceSnapshot {
        val temp = balances.toMutableMap()
        for ((currency, balance) in balances.entries) {
            if (balance.free.stripTrailingZeros() == BigDecimal.ZERO && balance.frozen.stripTrailingZeros() == BigDecimal.ZERO) {
                temp.remove(currency)
            }
        }
        balances = temp
        return this
    }
}
