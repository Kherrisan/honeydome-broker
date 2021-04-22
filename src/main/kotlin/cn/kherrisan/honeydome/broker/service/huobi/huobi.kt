package cn.kherrisan.honeydome.broker.service.huobi

import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.service.AbstractSpotService

class HuobiSpotService : AbstractSpotService(HUOBI, HuobiSpotApi()) {
    override val klineRequestLimit: Int
        get() = 300
}
