package cn.kherrisan.honeydome.broker.service

import cn.kherrisan.honeydome.broker.api.DecimalAdaptor
import cn.kherrisan.honeydome.broker.api.SpotApi
import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.common.Currency
import cn.kherrisan.honeydome.broker.coroutineFixedRateTimer
import cn.kherrisan.honeydome.broker.defaultCoroutineScope
import cn.kherrisan.honeydome.broker.randomId
import cn.kherrisan.honeydome.broker.repository.*
import cn.kherrisan.honeydome.broker.service.huobi.HuobiSpotService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

fun Exchange.spot(): SpotService = Service[this]

object Service {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val map: MutableMap<Exchange, SpotService> = mutableMapOf()

    operator fun get(exchange: Exchange): SpotService {
        return map[exchange] ?: error("Invalid exchange name: $exchange")
    }

    suspend fun setup() {
        logger.info("初始化 Service 模块")
        map[HUOBI] = HuobiSpotService()
        coroutineScope {
            map.values.forEach { launch { (it as AbstractSpotService).setup() } }
        }
    }
}

interface SpotService {
    suspend fun getCurrencys(): List<Currency>
    suspend fun getSymbols(): List<Symbol>
    suspend fun getKline(symbol: Symbol, period: KlinePeriod, start: ZonedDateTime, end: ZonedDateTime): List<Kline>
    suspend fun getKlineChannel(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): ReceiveChannel<Kline>

    suspend fun getBalance(): BalanceMap
    suspend fun getOrder(cid: String): Order
    suspend fun getFee(symbol: Symbol): Fee
    suspend fun cancelOrder(cid: String)
    suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String
    suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String
    suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String
    suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String
}

abstract class AbstractSpotService(private val exchange: Exchange, val api: SpotApi) :
    SpotService {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    abstract val klineRequestLimit: Int
    abstract val hasLogin: Boolean

    private lateinit var periodicalUpdateJob: Job
    lateinit var info: CommonInfo
    private val balanceMap = BalanceMap()

    open suspend fun setup() {
        logger.debug("Setup ${this::class.simpleName}")
        coroutineScope {
            launch { setupCommonInfo() }
            launch {
                api.setup()
                launch { setupBalance() }
                launch { setupOrder() }
                launch { setupOrderMatch() }
            }
        }
    }

    open suspend fun setupOrder() {
        if (!hasLogin) {
            return
        }
        api.subscribeOrderUpdate { updating ->
            logger.info("新的订单信息: $updating")
            OrderRepository.save(updating)
        }
    }

    open suspend fun setupOrderMatch() {
        if (!hasLogin) {
            return
        }
        api.subscribeOrderMatch { match ->
            logger.info("新的成交信息: $match")
            val order = OrderRepository.queryByExchangeAndOid(exchange, match.oid)
            if (order == null) {
                OrderMatchTempRepository.save(match)
            } else {
                order.matches.add(match)
                OrderRepository.save(order)
            }
        }
    }

    open suspend fun setupBalance() {
        if (!hasLogin) {
            return
        }
        api.getBalance().forEach { (currency, balance) ->
            balanceMap[currency] = balance
        }
        takeBalanceSnapshot()
        api.subscribeBalanceUpdate { (currency, balance) ->
            logger.info("新的账户余额变动: $currency: $balance")
            balanceMap[currency] = balance
            takeBalanceSnapshot()
        }
    }

    private suspend fun takeBalanceSnapshot() {
        val snapshot = BalanceSnapshot(exchange, balanceMap, ZonedDateTime.now())
        BalanceRepository.save(snapshot)
    }

    open suspend fun setupCommonInfo() {
        var dbCommonInfo = CommonInfoRepository.queryCommonInfo(exchange)
        if (dbCommonInfo == null) {
            logger.info("数据库中没有查到${exchange}基础数据，这是第一次启动，从${exchange} API获取基础数据。")
            val currencys = api.getCurrencys()
            val symbols = api.getSymbols()
            val currencyDecimalInfo = (api as DecimalAdaptor).getCurrencyDecimalInfo().toMutableMap()
            val symbolDecimalInfo = (api as DecimalAdaptor).getSymbolDecimalInfo().toMutableMap()
            dbCommonInfo = CommonInfo(exchange, currencys, symbols, symbolDecimalInfo, currencyDecimalInfo)
        }
        info = dbCommonInfo
        periodicalUpdateJob = coroutineFixedRateTimer(12 * 3600 * 1000) {
            logger.info("更新${exchange}基础数据。")
            info.currencys = api.getCurrencys()
            info.symbols = api.getSymbols()
            info.currencyDecimalInfo = (api as DecimalAdaptor).getCurrencyDecimalInfo().toMutableMap()
            info.symbolDecimalInfo = (api as DecimalAdaptor).getSymbolDecimalInfo().toMutableMap()
            CommonInfoRepository.save(info)
        }
    }

    override suspend fun getCurrencys(): List<Currency> = info.currencys

    override suspend fun getSymbols(): List<Symbol> = info.symbols

    override suspend fun getKline(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> {
        val readEnd = if (end.plusSeconds(period.seconds) >= ZonedDateTime.now()) {
            end.minusSeconds(period.seconds)
        } else {
            end
        }
        //包含start，不包含end
        //先从数据库里查，看缺多少。再从 api 查询缺少的分段
        val klines = KlineRepository.queryKline(exchange, symbol, period, start, readEnd).toMutableList()
        //以 1s 为最小粒度判断 klines 是否连续，是否有空洞
        val holes = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var cursor = start
        for (kline in klines) {
            if (cursor.plusSeconds(period.seconds) <= kline.time) {
                holes.add(Pair(cursor, kline.time))
            }
            cursor = kline.time.plusSeconds(period.seconds)
        }
        if (klines.isEmpty()) {
            //如果db里一无所有
            holes.add(Pair(start, readEnd))
        } else if (klines.last().time.plusSeconds(period.seconds) < readEnd) {
            //如果klines尾部连续缺失
            holes.add(Pair(klines.last().time, readEnd))
        }
        for (hole in holes) {
            fetchAndSaveKlineHole(symbol, period, readEnd, hole) {}
        }
        return KlineRepository.queryKline(exchange, symbol, period, start, readEnd).toMutableList()
    }

    @ExperimentalCoroutinesApi
    override suspend fun getKlineChannel(
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): ReceiveChannel<Kline> = defaultCoroutineScope().produce {
        val realEnd = if (end.plusSeconds(period.seconds) > ZonedDateTime.now()) {
            end.minusSeconds(period.seconds)
        } else {
            end
        }
        //先从数据库里查，看缺多少。再从 api 查询缺少的分段
        val klines = KlineRepository.queryKline(exchange, symbol, period, start, realEnd).toMutableList()
        //以 1s 为最小粒度判断 klines 是否连续，是否有空洞
        val holes = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        var cursor = start
        for (kline in klines) {
            if (cursor.plusSeconds(period.seconds) <= kline.time) {
                holes.add(Pair(cursor, kline.time))
            }
            cursor = kline.time.plusSeconds(period.seconds)
        }
        if (klines.isEmpty()) {
            holes.add(Pair(start, realEnd))
        } else if (klines.last().time.plusSeconds(period.seconds) < realEnd) {
            holes.add(Pair(klines.last().time, realEnd))
        }
        var klineIndex = 0
        var holeIndex = 0
        while (klineIndex < klines.size || holeIndex < holes.size) {
            if (klineIndex >= klines.size) {
                val hole = holes[holeIndex++]
                fetchAndSaveKlineHole(symbol, period, realEnd, hole) { send(it) }
            } else if (holeIndex >= holes.size) {
                send(klines[klineIndex++])
            } else {
                val kline = klines[klineIndex]
                val hole = holes[holeIndex]
                if (kline.time < hole.first) {
                    send(kline)
                    klineIndex++
                } else {
                    fetchAndSaveKlineHole(symbol, period, realEnd, hole) { send(it) }
                    holeIndex++
                }
            }
        }
        close()
    }

    private suspend fun fetchAndSaveKlineHole(
        symbol: Symbol,
        period: KlinePeriod,
        eend: ZonedDateTime,
        hole: Pair<ZonedDateTime, ZonedDateTime>,
        klineHandle: suspend (Kline) -> Unit
    ) {
        val periodCount =
            ((hole.second.toInstant().epochSecond - hole.first.toInstant().epochSecond) / period.seconds).toInt()
        for (s in 0 until periodCount step klineRequestLimit) {
            val start = hole.first.plusSeconds(s * period.seconds)
            val end = minOf(
                hole.first.plusSeconds((s + klineRequestLimit) * period.seconds),
                eend,
                Comparator { o1, o2 -> o1.compareTo(o2) })
            val holeKline = api.getKlines(
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
            holeKline.forEach { klineHandle(it) }
            KlineRepository.save(holeKline)
        }
    }

    override suspend fun getBalance(): BalanceMap {
        if (!hasLogin) {
            error("尚未登录，无法获取账户余额。")
        }
        return balanceMap
    }

    override suspend fun getOrder(cid: String): Order = OrderRepository.queryByCoid(cid)!!

    override suspend fun cancelOrder(cid: String) {
        val order = OrderRepository.queryByCoid(cid)!!
        api.cancelOrder(order.oid, order.symbol)
    }

    override suspend fun getFee(symbol: Symbol): Fee {
        if (!info.fees.contains(symbol)) {
            val fee = api.getFee(symbol)
            info.fees[symbol] = fee
            CommonInfoRepository.save(info)
        }
        return info.fees[symbol]!!
    }

    override suspend fun limitBuy(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String {
        val cid = "honeydome-${randomId()}"
        api.limitBuy(symbol, price, amount, cid)
        return cid
    }

    override suspend fun limitSell(symbol: Symbol, price: BigDecimal, amount: BigDecimal): String {
        val cid = "honeydome-${randomId()}"
        api.limitSell(symbol, price, amount, cid)
        return cid
    }

    override suspend fun marketBuy(symbol: Symbol, amount: BigDecimal): String {
        val cid = "honeydome-${randomId()}"
        api.marketBuy(symbol, amount, cid)
        return cid
    }

    override suspend fun marketSell(symbol: Symbol, amount: BigDecimal): String {
        val cid = "honeydome-${randomId()}"
        api.marketSell(symbol, amount, cid)
        return cid
    }
}
