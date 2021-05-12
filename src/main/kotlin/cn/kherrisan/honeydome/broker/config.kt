@file:UseSerializers(ZonedDateTimeSerializer::class, BigDecimalSerializer::class)

package cn.kherrisan.honeydome.broker

import com.github.jershell.kbson.BigDecimalSerializer
import com.mongodb.client.MongoDatabase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.litote.kmongo.KMongo
import org.litote.kmongo.findOneById
import org.litote.kmongo.getCollection
import org.litote.kmongo.save
import org.litote.kmongo.serialization.ZonedDateTimeSerializer
import java.time.ZonedDateTime

object ConfigRepository {

    val db: MongoDatabase

    init {
        val connectionStr = "mongodb://honeydome:honeydome_zou970514@localhost"
        val client = KMongo.createClient(connectionStr)
        db = client.getDatabase("honeydome")
    }

    fun queryByKey(key: String): ConfigItem? = db.getCollection<ConfigItem>().findOneById(key)
    fun queryAll() = db.getCollection<ConfigItem>().find().toList()
    fun save(key: String, value: String) = db.getCollection<ConfigItem>().save(ConfigItem(key, value))
}

@Serializable
data class ConfigItem(
    @SerialName("_id") val key: String,
    val value: String,
    val updateTime: ZonedDateTime = ZonedDateTime.now()
)

object Config {
    operator fun get(key: String): String? = ConfigRepository.queryByKey(key)?.value
    operator fun set(key: String, value: String) = ConfigRepository.save(key, value)
    fun getAll(): List<ConfigItem> = ConfigRepository.queryAll()
}
