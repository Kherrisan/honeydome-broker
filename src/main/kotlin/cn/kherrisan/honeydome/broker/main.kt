package cn.kherrisan.honeydome.broker

import java.util.*
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("main")
    logger.info("Launch Honeydome-Broker")
    val pq = PriorityQueue<Int>()
    for (i in 10 downTo 1) {
        pq.add(i)
    }
    println(pq)
}
