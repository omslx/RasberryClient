package xyz.mslx.rasberryClient.manager

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mslx.rasberryClient.model.SMPData
import xyz.mslx.rasberryClient.model.SMPStatus
import xyz.mslx.rasberryClient.model.WorldType
import java.sql.SQLException

class DatabaseManager(private val config: HikariConfig) {
    private val dataSource: HikariDataSource = HikariDataSource(config)

    init {

        CoroutineScope(Dispatchers.IO).launch {
            createTables()
        }
    }

    private fun createTables() {
        try {
            dataSource.connection.use { conn ->
                val statement = conn.createStatement()
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS smp_servers (
                        owner_uuid VARCHAR(36) PRIMARY KEY,
                        smp_id VARCHAR(36) NOT NULL,
                        smp_name VARCHAR(64) NOT NULL,
                        version VARCHAR(16) NOT NULL,
                        max_players INT NOT NULL,
                        whitelist TEXT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        ip_port VARCHAR(64) NOT NULL,
                        hardcore BOOLEAN NOT NULL,
                        world_type VARCHAR(16) NOT NULL
                    );
                """.trimIndent()
                )
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    suspend fun getSMPData(ownerUuid: String): SMPData? = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("SELECT * FROM smp_servers WHERE owner_uuid = ?")
                ps.setString(1, ownerUuid)
                val rs = ps.executeQuery()
                if (rs.next()) {
                    val whitelistRaw = rs.getString("whitelist")
                    val whitelist = whitelistRaw.split(",").filter { it.isNotEmpty() }.toMutableList()

                    return@withContext SMPData(
                        ownerUuid = rs.getString("owner_uuid"),
                        smpId = rs.getString("smp_id"),
                        smpName = rs.getString("smp_name"),
                        serverVersion = rs.getString("version"),
                        maxPlayers = rs.getInt("max_players"),
                        whitelistPlayers = whitelist,
                        status = SMPStatus.valueOf(rs.getString("status")),
                        serverIpPort = rs.getString("ip_port"),
                        hardcore = rs.getBoolean("hardcore"),
                        worldType = WorldType.valueOf(rs.getString("world_type"))
                    )
                }
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun saveSMPData(smpData: SMPData) = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement(
                    """
                    REPLACE INTO smp_servers (owner_uuid, smp_id, smp_name, version, max_players, whitelist, status, ip_port, hardcore, world_type)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                )
                ps.setString(1, smpData.ownerUuid)
                ps.setString(2, smpData.smpId)
                ps.setString(3, smpData.smpName)
                ps.setString(4, smpData.serverVersion)
                ps.setInt(5, smpData.maxPlayers)
                ps.setString(6, smpData.whitelistPlayers.joinToString(","))
                ps.setString(7, smpData.status.name)
                ps.setString(8, smpData.serverIpPort)
                ps.setBoolean(9, smpData.hardcore)
                ps.setString(10, smpData.worldType.name)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    suspend fun deleteSMPData(ownerUuid: String) = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val ps = conn.prepareStatement("DELETE FROM smp_servers WHERE owner_uuid = ?")
                ps.setString(1, ownerUuid)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        }
    }

    fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }
}