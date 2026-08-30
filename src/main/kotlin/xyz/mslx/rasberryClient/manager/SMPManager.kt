package xyz.mslx.rasberryClient.manager

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import xyz.mslx.rasberryClient.RasberryClient
import xyz.mslx.rasberryClient.model.SMPData
import xyz.mslx.rasberryClient.model.SMPStatus
import xyz.mslx.rasberryClient.model.SMPPayload
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SMPManager(private val plugin: RasberryClient) {
    private val activeSmpCache = ConcurrentHashMap<String, SMPData>()
    private val mm = MiniMessage.miniMessage()

    suspend fun loadPlayerData(uuid: UUID): SMPData? {
        val cached = activeSmpCache[uuid.toString()]
        if (cached != null) return cached

        val data = plugin.databaseManager.getSMPData(uuid.toString())
        if (data != null) {
            activeSmpCache[uuid.toString()] = data
        }
        return data
    }

    fun getCachedData(uuid: UUID): SMPData? = activeSmpCache[uuid.toString()]

    fun createRequest(smpData: SMPData) {
        activeSmpCache[smpData.ownerUuid] = smpData
        plugin.pluginScope.launch {
            plugin.databaseManager.saveSMPData(smpData)
        }

        val api = plugin.luminousApi
        if (api == null) {
            plugin.redisManager.publishPayload("smp:create", SMPPayload("CREATE", smpData))
            return
        }

        plugin.pluginScope.launch {
            smpData.status = SMPStatus.STARTING
            when (val res = api.createSmp(smpData.smpId, smpData.smpName, smpData.toSmpSettings())) {
                is ApiResult.Success -> {
                    notifyOwner(smpData.ownerUuid, "<gold>☁ Your SMP request was accepted — deploying server...</gold>")
                    pollUntilDone(smpData.ownerUuid, smpData.smpId)
                }
                is ApiResult.Failure -> {
                    smpData.status = SMPStatus.OFFLINE
                    persist(smpData)
                    val reason = when (res.kind) {
                        ApiError.FORBIDDEN -> "no active enterprise license"
                        ApiError.QUOTA -> "concurrent SMP limit reached"
                        ApiError.NETWORK -> "panel unreachable"
                        else -> res.message
                    }
                    notifyOwner(smpData.ownerUuid, "<red>✗ SMP deployment failed: $reason</red>")
                    plugin.logger.warning("SMP create failed for ${smpData.ownerUuid}: ${res.kind} ${res.message}")
                }
            }
        }
    }

    fun updateSettings(smpData: SMPData, action: String = "UPDATE") {
        plugin.redisManager.publishPayload("smp:settings", SMPPayload(action, smpData))
        plugin.pluginScope.launch {
            plugin.databaseManager.saveSMPData(smpData)
        }
    }

    fun deleteRequest(ownerUuid: String) {
        val data = activeSmpCache.remove(ownerUuid)
        val api = plugin.luminousApi
        if (data != null && api != null) {
            plugin.pluginScope.launch {
                when (val res = api.deleteSmp(data.smpId)) {
                    is ApiResult.Success -> notifyOwner(ownerUuid, "<red>Your SMP server has been deleted.</red>")
                    is ApiResult.Failure -> {
                        notifyOwner(ownerUuid, "<red>✗ SMP deletion failed: ${res.message}</red>")
                        plugin.logger.warning("SMP delete failed for $ownerUuid: ${res.kind} ${res.message}")
                    }
                }
            }
        } else if (data != null) {
            plugin.redisManager.publishPayload("smp:delete", SMPPayload("DELETE", data))
        }
        plugin.pluginScope.launch {
            plugin.databaseManager.deleteSMPData(ownerUuid)
        }
    }

    fun handleStatusUpdateFromBackend(payload: SMPPayload) {
        val data = payload.smpData
        activeSmpCache[data.ownerUuid] = data
        plugin.pluginScope.launch {
            plugin.databaseManager.saveSMPData(data)
        }
    }


    private fun pollUntilDone(ownerUuid: String, smpId: String) {
        plugin.pluginScope.launch {
            val api = plugin.luminousApi ?: return@launch
            val intervalMs = (plugin.config.getInt("luminous.poll_interval_ticks", 60).coerceAtLeast(2)) * 50L
            val deadline = System.currentTimeMillis() + 10 * 60_000L // حداکثر ۱۰ دقیقه

            while (System.currentTimeMillis() < deadline && plugin.isEnabled) {
                delay(intervalMs)
                val data = activeSmpCache[ownerUuid] ?: return@launch // در این فاصله حذف شده

                when (val res = api.getSmpStatus(smpId)) {
                    is ApiResult.Success -> {
                        when (res.data.status) {
                            "running" -> {
                                data.status = SMPStatus.ONLINE
                                data.serverIpPort = res.data.ipPort ?: ""
                                persist(data)
                                notifyOwner(
                                    ownerUuid,
                                    "<green>✔ Your SMP is live! </green><yellow>${data.serverIpPort}</yellow> <gray>(left-click it in /smp to connect)</gray>"
                                )
                                return@launch
                            }
                            "failed" -> {
                                data.status = SMPStatus.OFFLINE
                                persist(data)
                                notifyOwner(ownerUuid, "<red>✗ SMP build failed on the node. Try again later.</red>")
                                return@launch
                            }
                            "stopped" -> {
                                data.status = SMPStatus.OFFLINE
                                persist(data)
                                notifyOwner(ownerUuid, "<yellow>Your SMP is currently stopped — start it from the panel.</yellow>")
                                return@launch
                            }
                            else -> data.status = SMPStatus.STARTING
                        }
                    }
                    is ApiResult.Failure -> {
                        plugin.logger.fine("SMP status poll failed for $smpId: ${res.kind} ${res.message}")
                    }
                }
            }
            activeSmpCache[ownerUuid]?.let { stale ->
                stale.status = SMPStatus.OFFLINE
                persist(stale)
                notifyOwner(ownerUuid, "<red>✗ SMP deployment timed out.</red>")
            }
        }
    }

    private fun persist(data: SMPData) {
        plugin.pluginScope.launch {
            plugin.databaseManager.saveSMPData(data)
        }
    }

    private fun notifyOwner(ownerUuid: String, miniMessage: String) {
        plugin.pluginScope.launch {
            val uuid = runCatching { UUID.fromString(ownerUuid) }.getOrNull() ?: return@launch
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.getPlayer(uuid)?.sendMessage(mm.deserialize(miniMessage))
            })
        }
    }
}