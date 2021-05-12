package cn.kherrisan.honeydome.broker.engine

import cn.kherrisan.honeydome.broker.common.*
import cn.kherrisan.honeydome.broker.engine.strategy.Strategy
import cn.kherrisan.honeydome.broker.randomId
import cn.kherrisan.honeydome.broker.repository.OrderRepository
import cn.kherrisan.honeydome.broker.repository.ProcessRepository
import cn.kherrisan.honeydome.broker.service.Service
import cn.kherrisan.honeydome.broker.toZonedDateTime
import java.math.BigDecimal
import java.time.ZonedDateTime

abstract class Context(
    val process: Process,
    val strategy: Strategy
) {
    abstract suspend fun getCurrencys(exchange: Exchange): List<Currency>
    abstract suspend fun getSymbols(exchange: Exchange): List<Symbol>
    abstract suspend fun getKline(
        exchange: Exchange,
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline>

    abstract suspend fun getKline(exchange: Exchange, symbol: Symbol, period: KlinePeriod, time: ZonedDateTime): Kline
    abstract suspend fun getBalance(exchange: Exchange): BalanceMap
    abstract suspend fun getOrder(exchange: Exchange, cid: String): Order
    abstract suspend fun cancelOrder(exchange: Exchange, cid: String)
    abstract suspend fun limitBuy(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order
    abstract suspend fun limitSell(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order
    abstract suspend fun marketBuy(exchange: Exchange, symbol: Symbol, amount: BigDecimal): Order
    abstract suspend fun marketSell(exchange: Exchange, symbol: Symbol, amount: BigDecimal): Order

    override fun toString(): String = "${process.pid}(${strategy.name})"
}

class FirmbargainContext(process: Process, strategy: Strategy) : Context(process, strategy) {

    override suspend fun getCurrencys(exchange: Exchange): List<Currency> = Service[exchange].getCurrencys()

    override suspend fun getSymbols(exchange: Exchange): List<Symbol> = Service[exchange].getSymbols()

    override suspend fun getKline(
        exchange: Exchange,
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> = Service[exchange].getKline(symbol, period, start, end)

    override suspend fun getKline(exchange: Exchange, symbol: Symbol, period: KlinePeriod, time: ZonedDateTime): Kline {
        val start = if (time.toInstant().toEpochMilli() % 1000 == 0L) time else time.minusSeconds(1)
        val end = start.plusSeconds(period.seconds)
        return Service[exchange].getKline(symbol, period, start, end).first()
    }

    override suspend fun getBalance(exchange: Exchange): BalanceMap = Service[exchange].getBalance()

    override suspend fun getOrder(exchange: Exchange, cid: String): Order = Service[exchange].getOrder(cid)

    override suspend fun cancelOrder(exchange: Exchange, cid: String) = Service[exchange].cancelOrder(cid)

    override suspend fun limitBuy(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): String =
        Service[exchange].limitBuy(symbol, price, amount)

    override suspend fun limitSell(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): String =
        Service[exchange].limitSell(symbol, price, amount)

    override suspend fun marketBuy(exchange: Exchange, symbol: Symbol, amount: BigDecimal): String =
        Service[exchange].marketBuy(symbol, amount)

    override suspend fun marketSell(exchange: Exchange, symbol: Symbol, amount: BigDecimal): String =
        Service[exchange].marketSell(symbol, amount)
}

class BacktestContext(process: Process, strategy: Strategy) : Context(process, strategy) {

    private val virtualBalanceMap = mutableMapOf<Exchange, BalanceMap>()
    private val clock: ZonedDateTime = process.args["startTime"]!!.toZonedDateTime()
    private val openOrders: MutableList<Order> = mutableListOf()
    private val floatRate = 0.01

    init {
        Exchanges.forEach { virtualBalanceMap[it] = BalanceMap() }
    }

    override suspend fun getCurrencys(exchange: Exchange): List<Currency> = Service[exchange].getCurrencys()

    override suspend fun getSymbols(exchange: Exchange): List<Symbol> = Service[exchange].getSymbols()

    override suspend fun getKline(exchange: Exchange, symbol: Symbol, period: KlinePeriod, time: ZonedDateTime): Kline {
        val start = if (time.toInstant().toEpochMilli() % 1000 == 0L) time else time.minusSeconds(1)
        val end = start.plusSeconds(period.seconds)
        return Service[exchange].getKline(symbol, period, start, end).first()
    }

    override suspend fun getKline(
        exchange: Exchange,
        symbol: Symbol,
        period: KlinePeriod,
        start: ZonedDateTime,
        end: ZonedDateTime
    ): List<Kline> = Service[exchange].getKline(symbol, period, start, end)

    override suspend fun getBalance(exchange: Exchange): BalanceMap = virtualBalanceMap[exchange]!!

    override suspend fun getOrder(exchange: Exchange, cid: String): Order = OrderRepository.queryByCoid(cid)!!

    override suspend fun cancelOrder(exchange: Exchange, cid: String) {
        val order = OrderRepository.queryByCoid(cid)!!
        when (order.state) {
            OrderState.FILLED, OrderState.CANCELED, OrderState.FAILED -> return
            else -> order.state = OrderState.CANCELED
        }
        OrderRepository.save(order)
    }

    private fun floatPrice(order: Order): BigDecimal {
        return if (order.side == OrderSide.BUY) {
            order.price * (1 + floatRate).toBigDecimal()
        } else {
            order.price / (1 + floatRate).toBigDecimal()
        }
    }

    private suspend fun matchOrderAtPrice(order: Order, price: BigDecimal) {
        val baseAmount = if (order.type == OrderType.MARKET && order.side == OrderSide.BUY)
            order.amount / price
        else
            order.amount
        val quoteAmount = if (order.type == OrderType.MARKET && order.side == OrderSide.BUY)
            order.amount
        else
            order.amount * price
        val feeRate = Service[order.exchange].getFee(order.symbol).maker
        val feeCurrency = if (order.side == OrderSide.BUY) order.symbol.base else order.symbol.quote
        val fee = if (order.side == OrderSide.BUY)
            baseAmount * feeRate
        else
            quoteAmount * feeRate
        val match = OrderMatch(
            randomId(),
            order.coid,
            TradeRole.MAKER,
            price,
            baseAmount,
            fee,
            feeCurrency,
            clock
        )
        order.matches.add(match)
        if (order.side == OrderSide.BUY) {
            virtualBalanceMap[order.exchange]!![order.symbol.base].free += baseAmount
            virtualBalanceMap[order.exchange]!![order.symbol.quote].free -= quoteAmount
        } else {
            virtualBalanceMap[order.exchange]!![order.symbol.base].free -= baseAmount
            virtualBalanceMap[order.exchange]!![order.symbol.quote].free += quoteAmount
        }
        order.balanceSnapshot = virtualBalanceMap[order.exchange]!!
        OrderRepository.save(order)
    }

    private suspend fun submitOrder(order: Order) {
        val kline = getKline(order.exchange, order.symbol, KlinePeriod.MINUTE, order.createTime)
        if (order.type == OrderType.MARKET || (order.price <= kline.high && order.price >= kline.low)) {
            //市价单
            //或者order出价正好落在kline的high和low之内，则可以成交
            val finalPrice = floatPrice(order)
            matchOrderAtPrice(order, finalPrice)
        } else {
            //存入openOrders，等待成交
            openOrders.add(order)
        }
    }

    private suspend fun createOrder(
        exchange: Exchange,
        symbol: Symbol,
        side: OrderSide,
        type: OrderType,
        price: BigDecimal,
        amount: BigDecimal
    ): Order {
        val coid = "honeydome-${randomId()}"
        val order = Order(
            exchange,
            coid,
            coid,
            symbol,
            OrderState.CREATED,
            side,
            price,
            amount,
            clock,
            type
        )
        process.orders.add(order)
        ProcessRepository.save(process)
        submitOrder(order)
        return order
    }

    override suspend fun limitBuy(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order {
        return createOrder(exchange, symbol, OrderSide.BUY, OrderType.LIMIT, price, amount)
    }

    override suspend fun limitSell(exchange: Exchange, symbol: Symbol, price: BigDecimal, amount: BigDecimal): Order {
        return createOrder(exchange, symbol, OrderSide.SELL, OrderType.LIMIT, price, amount)
    }

    override suspend fun marketBuy(exchange: Exchange, symbol: Symbol, amount: BigDecimal): Order {
        return createOrder(exchange, symbol, OrderSide.BUY, OrderType.MARKET, BigDecimal.ZERO, amount)
    }

    override suspend fun marketSell(exchange: Exchange, symbol: Symbol, amount: BigDecimal): Order {
        return createOrder(exchange, symbol, OrderSide.SELL, OrderType.MARKET, BigDecimal.ZERO, amount)
    }
}
