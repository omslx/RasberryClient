package xyz.mslx.rasberryClient.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import xyz.mslx.rasberryClient.model.SMPPayload

class RedisManager(private val host: String, private val port: Int, private val scope: CoroutineScope) {
    private val pool = JedisPool(host, port)
    private val json = Json { ignoreUnknownKeys = true }

    fun startListening(onMessageReceived: (String, SMPPayload) -> Unit) {
        scope.launch(Dispatchers.IO) {
            pool.resource.use { jedis ->
                jedis.subscribe(object : JedisPubSub() {
                    override fun onMessage(channel: String, message: String) {
                        try {
                            val payload = json.decodeFromString<SMPPayload>(message)
                            onMessageReceived(channel, payload)
                        } catch (e: Exception) {
                        }
                    }
                }, "smp:status_update")
            }
        }
    }

    fun publishPayload(channel: String, payload: SMPPayload) {
        scope.launch(Dispatchers.IO) {
            pool.resource.use { jedis ->
                val message = json.encodeToString(payload)
                jedis.publish(channel, message)
            }
        }
    }

    fun close() {
        pool.close()
    }
}