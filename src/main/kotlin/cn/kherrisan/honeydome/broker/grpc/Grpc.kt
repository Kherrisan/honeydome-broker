package cn.kherrisan.honeydome.broker.grpc

import org.slf4j.LoggerFactory

object Grpc {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun setup() {
        logger.info("初始化 Grpc 模块")
    }
}
