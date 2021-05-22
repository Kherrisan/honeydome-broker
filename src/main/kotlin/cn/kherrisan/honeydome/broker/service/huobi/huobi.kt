package cn.kherrisan.honeydome.broker.service.huobi

import cn.kherrisan.honeydome.broker.Config
import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.coroutineFixedRateTimer
import cn.kherrisan.honeydome.broker.repository.OrderMatchTempRepository
import cn.kherrisan.honeydome.broker.repository.OrderRepository
import cn.kherrisan.honeydome.broker.service.AbstractSpotService
import cn.kherrisan.kommons.get
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            launch {
                setupCommonInfo()
                huobiApi.currencys = info.currencys
            }
            launch {
                api.setup()
                delay(200)
                setupBalance()
                delay(200)
                setupOrder()
                delay(200)
                setupOrderMatch()
                delay(200)
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
