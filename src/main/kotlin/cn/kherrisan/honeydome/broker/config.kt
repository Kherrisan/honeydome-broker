package cn.kherrisan.honeydome.broker

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader

object Config {

    private var root: JsonElement? = null

    init {
        val inputStream = javaClass.classLoader.getResourceAsStream("config.json")
        if (inputStream != null) {
            val reader = BufferedReader(InputStreamReader(inputStream))
            root = JsonParser.parseReader(reader)
            reader.close()
        }
    }

    operator fun get(key: String): JsonElement {
        return root!!.asJsonObject.get(key)
    }
}
