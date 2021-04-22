package cn.kherrisan.honeydome.broker.service.huobi

import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI
import cn.kherrisan.honeydome.broker.service.AbstractSpotFirmbargainService

class HuobiSpotFirmbargainService : AbstractSpotFirmbargainService(HUOBI, HuobiSpotApi()) {
    override val klineRequestLimit: Int
        get() = 300
}
