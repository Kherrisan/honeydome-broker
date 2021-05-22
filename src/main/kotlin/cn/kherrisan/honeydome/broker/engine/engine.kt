package cn.kherrisan.honeydome.broker.engine

import cn.kherrisan.honeydome.broker.engine.strategy.Strategy
import cn.kherrisan.honeydome.broker.engine.strategy.TestStrategy
import cn.kherrisan.honeydome.broker.randomId
import cn.kherrisan.honeydome.broker.repository.ProcessRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.reflect.full.primaryConstructor

object Engine {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val processContextMap = mutableMapOf<String, Context>()
    private val strategyMap = mutableMapOf(
        "TestStrategy" to TestStrategy::class
    )

    suspend fun setup() {
        logger.info("初始化 Engine 模块")
    }

    suspend fun launch(name: String, backtest: Boolean, args: Map<String, String>): String {
        logger.info(
            "正在启动 ${if (backtest) "回测" else "实盘"} 策略 $name, 参数: ${
                args.entries.map { "${it.key}:${it.value}" }.joinToString(",")
            }"
        )
        val process = Process(randomId(), name, ZonedDateTime.now(), ZonedDateTime.now(), backtest, args)
        ProcessRepository.save(process)
        val strategyKClass = strategyMap[name]!!
        val strategy = strategyKClass.primaryConstructor!!.call(args) as Strategy
        val context = if (backtest) BacktestContext(process, strategy) else FirmbargainContext(process, strategy)
        processContextMap[process.pid] = context
        GlobalScope.launch {
            context.strategy.setup(context)
            context.strategy.run(context)
        }
        logger.info("进程 $context 已启动")
        return process.pid
    }

    suspend fun terminate(pid: String) {
        if (!processContextMap.contains(pid)) {
            return
        }
        val context = processContextMap[pid]!!
        logger.info("正在关停进程 $context")
        context.process.terminateTime = ZonedDateTime.now()
        context.strategy.stop(context)
        ProcessRepository.save(context.process)
        processContextMap.remove(pid)
        logger.info("进程 $context 已关停")
    }
}
