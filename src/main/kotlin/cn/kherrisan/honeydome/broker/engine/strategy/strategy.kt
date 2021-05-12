package cn.kherrisan.honeydome.broker.engine.strategy

import cn.kherrisan.honeydome.broker.engine.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class Strategy(
    val args: Map<String, String>
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)
    abstract suspend fun setup(context: Context)
    abstract suspend fun run(context: Context)
    abstract suspend fun stop(context: Context)
}

class TestStrategy(args: Map<String, String>) : Strategy(args){
    override suspend fun setup(context: Context) {
        TODO("Not yet implemented")
    }

    override suspend fun run(context: Context) {
        TODO("Not yet implemented")
    }

    override suspend fun stop(context: Context) {
        TODO("Not yet implemented")
    }
}
