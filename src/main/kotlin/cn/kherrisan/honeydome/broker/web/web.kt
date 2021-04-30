package cn.kherrisan.honeydome.broker.web

import cn.kherrisan.honeydome.broker.common.BalanceSnapshot
import cn.kherrisan.honeydome.broker.common.KlinePeriod
import cn.kherrisan.honeydome.broker.gson
import cn.kherrisan.honeydome.broker.repository.BalanceRepository
import cn.kherrisan.honeydome.broker.service.spot
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.openapi.Operation
import io.vertx.ext.web.openapi.RouterBuilder
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.receiveChannelHandler
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

const val API_YAML_PATH = "src/main/openapi/api.yaml"

object Web {
    private val logger = LoggerFactory.getLogger(this::class.java)
    fun setup() {
        logger.info("初始化 Web 模块")
        Vertx.vertx().deployVerticle(WebVerticle())
    }
}

suspend fun handleGetKlines(ctx: RoutingContext) = ctx.request().run {
    val exchange = getParam("exchange").toLowerCase()
    val symbol = getParam("symbol").toLowerCase()
    val period = KlinePeriod.valueOf(getParam("period").toUpperCase())
    val end = getParam("end")?.let { ZonedDateTime.parse(it) } ?: ZonedDateTime.now()
    val start = getParam("start")?.let { ZonedDateTime.parse(it) } ?: end.minusSeconds(20 * period.seconds)
    val klines = exchange.spot().getKline(symbol, period, start, end)
    val resp = ctx.response()
    resp.isChunked = true
    resp.end(gson().toJson(klines))
}

suspend fun handleQueryLatestBalance(ctx: RoutingContext) = ctx.request().run {
    val exchange = getParam("exchange").toLowerCase()
    val snapshot = BalanceSnapshot(exchange, ZonedDateTime.now(), exchange.spot().getBalance()).ignoreZeroBalance()
    response().end(gson().toJson(snapshot))
}

suspend fun handleQueryHistoryBalance(ctx: RoutingContext) = ctx.request().run {
    val exchange = getParam("exchange").toLowerCase()
    val end = getParam("end")?.let { ZonedDateTime.parse(it) } ?: ZonedDateTime.now()
    val start = getParam("start")?.let { ZonedDateTime.parse(it) } ?: end.minusDays(5)
    val snapshots = BalanceRepository.queryByExchangeAndDatetimeRange(exchange, start, end)
    snapshots.forEach { it.ignoreZeroBalance() }
    response().end(gson().toJson(snapshots))
}

class WebVerticle : CoroutineVerticle() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun start() {
        val routerBuilder: RouterBuilder
        try {
            routerBuilder = awaitResult { RouterBuilder.create(vertx, API_YAML_PATH, it) }
        } catch (e: Exception) {
            logger.error(e.message)
            e.printStackTrace()
            return
        }
        routerBuilder.operation("helloWorld").suspendHandler { ctx -> ctx.response().end("Hello World!") }
        routerBuilder.operation("getKlines").suspendHandler { ctx -> handleGetKlines(ctx) }
        routerBuilder.operation("queryLatestBalance").suspendHandler { ctx -> handleQueryLatestBalance(ctx) }
        routerBuilder.operation("queryHistoryBalance").suspendHandler { ctx -> handleQueryHistoryBalance(ctx) }
        vertx.createHttpServer(HttpServerOptions().setPort(7800).setCompressionSupported(true))
            .requestHandler(routerBuilder.createRouter()).listen()
            .await()
    }

    private fun Operation.suspendHandler(handler: suspend (RoutingContext) -> Unit) {
        val adaptor = vertx.receiveChannelHandler<RoutingContext>()
        launch {
            while (true) {
                val ctx = adaptor.receive()
                handler(ctx)
            }
        }
        handler(adaptor)
    }
}
