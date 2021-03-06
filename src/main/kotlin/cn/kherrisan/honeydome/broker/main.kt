package cn.kherrisan.honeydome.broker

import cn.kherrisan.honeydome.broker.engine.Engine
import cn.kherrisan.honeydome.broker.repository.Mongodb
import cn.kherrisan.honeydome.broker.service.Service
import cn.kherrisan.honeydome.broker.web.Web
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("main")

fun main() {
    logger.info("启动 honeydome-broker")
    runBlocking(defaultCoroutineScope().coroutineContext) {
        Mongodb.setup()
        Service.setup()
        Web.setup()
        Engine.setup()
        logger.info("全部模块初始化成功")
    }
}
