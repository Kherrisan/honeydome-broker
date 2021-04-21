package cn.kherrisan.honeydome.broker

import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*

fun main() {
    val logger = LoggerFactory.getLogger("main")
    logger.info("Launch Honeydome-Broker")
    val pq = PriorityQueue<Int>()
    for (i in 10 downTo 1) {
        pq.add(i)
    }
    println(pq)
}
