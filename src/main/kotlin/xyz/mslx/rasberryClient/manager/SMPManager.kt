package xyz.mslx.rasberryClient.manager

import kotlinx.coroutines.launch
import xyz.mslx.rasberryClient.RasberryClient
import xyz.mslx.rasberryClient.model.SMPData
import xyz.mslx.rasberryClient.model.SMPPayload
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SMPManager(private val plugin: RasberryClient) {
    private val activeSmpCache = ConcurrentHashMap<String, SMPData>()

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
        plugin.redisManager.publishPayload("smp:create", SMPPayload("CREATE", smpData))

        plugin.pluginScope.launch {
            plugin.databaseManager.saveSMPData(smpData)
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
        if (data != null) {
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
}