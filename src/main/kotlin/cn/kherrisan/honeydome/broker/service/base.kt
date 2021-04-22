package cn.kherrisan.honeydome.broker.service

import cn.kherrisan.honeydome.broker.Config
import cn.kherrisan.honeydome.broker.api.DecimalAdaptor
import cn.kherrisan.honeydome.broker.api.SpotApi
import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.common.Currency
import cn.kherrisan.honeydome.broker.coroutineFixedRateTimer
import cn.kherrisan.honeydome.broker.randomId
import cn.kherrisan.honeydome.broker.repository.BalanceRepository
import cn.kherrisan.honeydome.broker.repository.CommonInfoRepository
import cn.kherrisan.honeydome.broker.repository.KlineRepository
import cn.kherrisan.kommons.get
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

interface SpotService {
    suspend fun getKline(symbol: Symbol, period: KlinePeriod, start: ZonedDateTime, end: ZonedDateTime): List<Kline>
    suspend fun getBalance(): Map<Currency, Balance>
    suspend fun getOrder(cid: String): Order
    suspend fun getOrderMatch(cid: String): List<OrderMatch>
    suspend fun cancelOrder(cid: String)
    suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order
    suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order
    suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): Order
    suspend fun marketSell(symbol: Symbol, amount: BigDecimal): Order
}

abstract class AbstractSpotService(private val exchange: Exchange, private val spotApi: SpotApi) : SpotService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    abstract val klineRequestLimit: Int

    private lateinit var periodicalUpdateJob: Job
    private lateinit var info: CommonInfo
    private lateinit var apiKey: String
    private lateinit var secretKey: String
    private val hasLogined: Boolean
        get() = this::apiKey.isInitialized && this::secretKey.isInitialized
            && apiKey.isNotEmpty() && secretKey.isNotEmpty()

    open suspend fun setup() {
        var dbCommonInfo = CommonInfoRepository.queryCommonInfo(exchange)
        if (dbCommonInfo == null) {
            logger.info("数据库中没有查到${exchange}基础数据，这是第一次启动，从${exchange} API获取基础数据。")
            val currencys = spotApi.getCurrencys()
            val symbols = spotApi.getSymbols()
            val currencyDecimalInfo = (spotApi as DecimalAdaptor).getCurrencyDecimalInfo().toMutableMap()
            val symbolDecimalInfo = (spotApi as DecimalAdaptor).getSymbolDecimalInfo().toMutableMap()
            dbCommonInfo = CommonInfo(exchange, currencys, symbols, symbolDecimalInfo, currencyDecimalInfo)
        }
        info = dbCommonInfo
        periodicalUpdateJob = coroutineFixedRateTimer(12 * 3600 * 1000) {
            logger.info("更新${exchange}基础数据。")
            info.currencys = spotApi.getCurrencys()
            info.symbols = spotApi.getSymbols()
            info.currencyDecimalInfo = (spotApi as DecimalAdaptor).getCurrencyDecimalInfo().toMutableMap()
            info.symbolDecimalInfo = (spotApi as DecimalAdaptor).getSymbolDecimalInfo().toMutableMap()
            CommonInfoRepository.save(info)
        }
        apiKey = Config["exchange"]["huobi"]["apiKey"].asString
        secretKey = Config["exchange"]["huobi"]["secretKey"].asString
    }

    override suspend fun getKline(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {
        val eend = if (end.plusSeconds(period.seconds) >= ZonedDateTime.now()) {
            end.minusSeconds(period.seconds)
        } else {
            end
        }
        //先从数据库里查，看缺多少。再从 api 查询缺少的分段
        val klines = KlineRepository.queryKline(exchange, symbol, period, start, eend).toMutableList()
        //以 1s 为最小粒度判断 klines 是否连续，是否有空洞
        val holes = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var cursor = start
        /**
         * 举例：
         * 1   2   3   5   6   9   14  15  16  19  20
         * 从上述 kline 中查询 start=0,end=22
         */
        for (kline in klines) {
            if (cursor.plusSeconds(period.seconds) <= kline.time) {
                holes.add(Pair(cursor, kline.time))
            }
            cursor = kline.time.plusSeconds(period.seconds)
        }
        if (klines.isEmpty()) {
            holes.add(Pair(start, eend))
        } else if (klines.last().time.plusSeconds(period.seconds) < eend) {
            holes.add(Pair(klines.last().time, eend))
        }
        for (hole in holes) {
            val periodCount =
                (hole.second.toInstant().epochSecond - hole.first.toInstant().epochSecond) / period.seconds
            for (s in 0 until periodCount step klineRequestLimit.toLong()) {
                val start = hole.first.plusSeconds(s * period.seconds)
                val end = minOf(
                    hole.first.plusSeconds((s + klineRequestLimit) * period.seconds),
                    eend,
                    Comparator { o1, o2 -> o1.compareTo(o2) })
                val holeKline = spotApi.getKlines(
                    symbol,
                    period,
                    start,
                    end
                ).toMutableList()
                delay(200)
                //如果最后一个 kline 是当天（当分钟、当小时）的数据，则不插入数据库
                if (holeKline.last().time.plusSeconds(period.seconds) >= ZonedDateTime.now()) {
                    holeKline.removeLast()
                }
//                println(holeKline.first().time)
//                println(holeKline.last().time)
                KlineRepository.save(holeKline)
            }
        }
        return KlineRepository.queryKline(exchange, symbol, period, start, eend).toMutableList()
    }

    override suspend fun getBalance(): Map<Currency, Balance> {
        if (!hasLogined) {
            logger.error("尚未登录，无法获取账户余额。")
            return emptyMap()
        }
        val balances = spotApi.getBalance()
        val snapshot = BalanceSnapshot(exchange, ZonedDateTime.now(), balances)
        BalanceRepository.save(snapshot)
        return balances
    }

    override suspend fun getOrder(cid: String): Order {
        TODO("Not yet implemented")
    }

    override suspend fun getOrderMatch(cid: String): List<OrderMatch> {
        TODO("Not yet implemented")
    }

    override suspend fun cancelOrder(cid: String) {

    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order {
        val coid = randomId()
        val order = Order(
            HUOBI,
            "",
            coid,
            symbol,
            OrderState.CREATED,
            OrderSide.BUY,
            price,
            amount,
            ZonedDateTime.now(),
            OrderType.LIMIT
        )
        order.oid = spotApi.limitBuy(symbol, price, amount)
        return order
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order {
        val coid = randomId()
        val order = Order(
            HUOBI,
            "",
            coid,
            symbol,
            OrderState.CREATED,
            OrderSide.SELL,
            price,
            amount,
            ZonedDateTime.now(),
            OrderType.LIMIT
        )
        order.oid = spotApi.limitBuy(symbol, price, amount)
        return order
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): Order {
        val coid = randomId()
        val order = Order(
            HUOBI,
            "",
            coid,
            symbol,
            OrderState.CREATED,
            OrderSide.BUY,
            0f.toBigDecimal(),
            amount,
            ZonedDateTime.now(),
            OrderType.MARKET
        )
        order.oid = spotApi.marketBuy(symbol, amount)
        return order
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): Order {
        val coid = randomId()
        val order = Order(
            HUOBI,
            "",
            coid,
            symbol,
            OrderState.CREATED,
            OrderSide.SELL,
            BigDecimal.ZERO,
            amount,
            ZonedDateTime.now(),
            OrderType.MARKET
        )
        order.oid = spotApi.marketSell(symbol, amount)
        return order
    }
}
