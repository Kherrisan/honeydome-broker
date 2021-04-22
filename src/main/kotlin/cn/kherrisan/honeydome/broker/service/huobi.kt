package cn.kherrisan.honeydome.broker.service

import cn.kherrisan.honeydome.broker.api.huobi.HuobiSpotApi
import cn.kherrisan.honeydome.broker.common.HUOBI

class HuobiSpotService : AbstractSpotService(HUOBI, HuobiSpotApi()) {
    override val klineRequestLimit: Int
        get() = 300
}
