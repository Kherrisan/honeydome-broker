package cn.kherrisan.honeydome.broker.service.huobi

import cn.kherrisan.honeydome.broker.Config
import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.service.AbstractSpotService
import cn.kherrisan.kommons.get

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
        super.setup()
    }
}
