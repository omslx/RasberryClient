package xyz.mslx.rasberryClient

import com.zaxxer.hikari.HikariConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin
import xyz.mslx.rasberryClient.Listener.GUIListener
import xyz.mslx.rasberryClient.manager.DatabaseManager
import xyz.mslx.rasberryClient.manager.RedisManager
import xyz.mslx.rasberryClient.manager.SMPManager
import java.util.concurrent.Executors

class RasberryClient : JavaPlugin() {

    private val pluginExecutor = Executors.newFixedThreadPool(4)
    val pluginScope = CoroutineScope(SupervisorJob() + pluginExecutor.asCoroutineDispatcher())

    lateinit var databaseManager: DatabaseManager
    lateinit var redisManager: RedisManager
    lateinit var smpManager: SMPManager
    lateinit var guiManager: GUIManager

    var economy: Economy? = null

    override fun onEnable() {
        saveDefaultConfig()

        if (!setupEconomy()) {
            logger.severe("Vault dependency not found or no economy provider installed! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        val dbConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://${config.getString("database.host")}:${config.getInt("database.port")}/${config.getString("database.name")}"
            username = config.getString("database.username")
            password = config.getString("database.password")
            maximumPoolSize = 10
            minimumIdle = 2
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        databaseManager = DatabaseManager(dbConfig)

        val redisHost = config.getString("redis.host", "127.0.0.1")!!
        val redisPort = config.getInt("redis.port", 6379)
        redisManager = RedisManager(redisHost, redisPort, pluginScope)

        smpManager = SMPManager(this)
        guiManager = GUIManager(this)

        redisManager.startListening { channel, payload ->
            if (channel == "smp:status_update") {
                smpManager.handleStatusUpdateFromBackend(payload)
            }
        }

        server.pluginManager.registerEvents(GUIListener(this), this)

        getCommand("smp")?.setExecutor { sender, _, _, _ ->
            if (sender is org.bukkit.entity.Player) {
                guiManager.openMainMenu(sender)
            }
            true
        }

        logger.info("SMP Lobby Client successfully enabled with Multi-Version GUI support.")
    }

    override fun onDisable() {
        pluginScope.cancel()
        pluginExecutor.shutdown()

        if (::databaseManager.isInitialized) databaseManager.close()
        if (::redisManager.isInitialized) redisManager.close()

        logger.info("SMP Lobby Client safely disabled and connections closed.")
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) return false
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return economy != null
    }
}
