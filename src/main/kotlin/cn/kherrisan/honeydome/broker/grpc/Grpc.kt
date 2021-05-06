package cn.kherrisan.honeydome.broker.grpc

import io.vertx.core.Vertx
import io.vertx.grpc.VertxServerBuilder
import org.slf4j.LoggerFactory

object Grpc {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun setup() {
        logger.info("初始化 Grpc 模块")
        VertxServerBuilder.forAddress(Vertx.vertx(), "localhost", 11111).nettyBuilder()
            .addService(object : SpotMarketGrpcKt.SpotMarketCoroutineImplBase() {
                override suspend fun ticker(request: Broker.SimpleDataRequest): Broker.TickerResponse {

                }
            }).build()
    }
}
