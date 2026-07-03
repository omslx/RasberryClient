package xyz.mslx.rasberryClient.model

import kotlinx.serialization.Serializable

@Serializable
enum class SMPStatus {
    ONLINE, OFFLINE, STARTING
}

@Serializable
enum class WorldType {
    DEFAULT, FLAT, AMPLIFIED
}

@Serializable
data class SMPData(
    val ownerUuid: String,
    val smpId: String,
    var smpName: String,
    var serverVersion: String,
    var maxPlayers: Int = 10,
    val whitelistPlayers: MutableList<String> = mutableListOf(),
    var status: SMPStatus = SMPStatus.OFFLINE,
    var serverIpPort: String = "",
    var hardcore: Boolean = false,
    var worldType: WorldType = WorldType.DEFAULT
)

@Serializable
data class SMPPayload(
    val action: String, // "CREATE", "DELETE", "UPGRADE", "TOGGLE_HARDCORE"
    val smpData: SMPData
)