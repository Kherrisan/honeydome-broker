package cn.kherrisan.honeydome.broker.service

import cn.kherrisan.honeydome.broker.api.SpotApi
import cn.kherrisan.honeydome.broker.common.*
import java.math.BigDecimal
import java.time.ZonedDateTime

interface SpotService {
    suspend fun getKline(symbol: Symbol, period: KlinePeriod, start: ZonedDateTime, end: ZonedDateTime): List<Kline>
    suspend fun getBalance(): Map<Currency, Balance>
    suspend fun getOrder(cid: String): Order
    suspend fun getOrderMatch(cid: String): List<OrderMatch>
    suspend fun cancelOrder(cid: String)
    suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String
    suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String
    suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String
    suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String
}

abstract class AbstractSpotService(val spotApi: SpotApi) : SpotService {

    abstract val klineRequestLimit: Int

    init {
    	//初始化阶段的任务
        //1. 从 db 载入 currencyList，启动轮询
        //2. 从 db 载入 symbolList，启动轮询
        //3. 从 db 载入 decimalInfo，启动轮询
    }

    override suspend fun getKline(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {
        //先从数据库里查，看缺多少。再从 api 查询缺少的分段

    }

    override suspend fun getBalance(): Map<Currency, Balance> {
        TODO("Not yet implemented")
    }

    override suspend fun getOrder(cid: String): Order {
        TODO("Not yet implemented")
    }

    override suspend fun getOrderMatch(cid: String): List<OrderMatch> {
        TODO("Not yet implemented")
    }

    override suspend fun cancelOrder(cid: String) {
        TODO("Not yet implemented")
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String {
        TODO("Not yet implemented")
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String {
        TODO("Not yet implemented")
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String {
        TODO("Not yet implemented")
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String {
        TODO("Not yet implemented")
    }
}
