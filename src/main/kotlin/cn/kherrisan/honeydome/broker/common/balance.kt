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
    var balances: BalanceMap,
    val time: ZonedDateTime
)

@Serializable
data class BalanceMap(
    private val map: MutableMap<Currency, Balance> = mutableMapOf()
) {
    operator fun get(currency: Currency): Balance {
        return if (map.contains(currency)) {
            map[currency]!!
        } else {
            Balance()
        }
    }

    operator fun set(currency: Currency, balance: Balance) {
        map[currency] = balance
        if (balance.free == BigDecimal.ZERO && balance.frozen == BigDecimal.ZERO) {
            map.remove(currency)
        }
    }
}
