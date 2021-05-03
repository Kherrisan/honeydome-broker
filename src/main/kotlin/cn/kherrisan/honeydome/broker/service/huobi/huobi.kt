package cn.kherrisan.honeydome.broker.service.huobi

import cn.kherrisan.honeydome.broker.Config
import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.common.Symbol
import cn.kherrisan.honeydome.broker.coroutineFixedRateTimer
import cn.kherrisan.honeydome.broker.repository.OrderMatchTempRepository
import cn.kherrisan.honeydome.broker.repository.OrderRepository
import cn.kherrisan.honeydome.broker.service.AbstractSpotService
import cn.kherrisan.kommons.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.litote.kmongo.or
import java.math.BigDecimal

class HuobiSpotService : AbstractSpotService(HUOBI, HuobiSpotApi()) {
    override val klineRequestLimit: Int
        get() = 300

    override val hasLogin: Boolean
        get() = (api as HuobiSpotApi).let {
            it.apiKey.isNotEmpty() && it.secretKey.isNotEmpty()
        }

    override suspend fun setup() {
        val huobiApi = api as HuobiSpotApi
        huobiApi.apiKey = Config["exchange"]["huobi"]["apiKey"].asString
        huobiApi.secretKey = Config["exchange"]["huobi"]["secretKey"].asString
        logger.debug("Setup ${this::class.simpleName}")
        coroutineScope {
            launch { setupCommonInfo() }
            huobiApi.currencys = info.currencys
            launch {
                api.setup()
                launch { setupBalance() }
                launch { setupOrder() }
                launch { setupOrderMatch() }
            }
        }
    }

    override suspend fun setupOrderMatch() {
        super.setupOrderMatch()
        coroutineFixedRateTimer(5000) {
            OrderMatchTempRepository.queryAll().forEach { match ->
                OrderRepository.queryByExchangeAndOid(HUOBI, match.oid)?.let { order ->
                    order.matches.add(match)
                    OrderRepository.save(order)
                    OrderMatchTempRepository.delete(match)
                }
            }
        }
    }
}
