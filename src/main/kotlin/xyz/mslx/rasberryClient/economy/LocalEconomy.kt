package xyz.mslx.rasberryClient.economy

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import xyz.mslx.rasberryClient.RasberryClient
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Local (built-in) economy - NO Vault dependency at all.
 * Balances persist in the panel database (smp_economy table).
 * Loaded eagerly on enable; used directly by the GUI flow.
 */
class LocalEconomy(private val plugin: RasberryClient) {

    private val balances = ConcurrentHashMap<UUID, Double>()

    private fun uuidOf(name: String): UUID = Bukkit.getOfflinePlayer(name).uniqueId
    private fun key(p: OfflinePlayer): UUID = p.uniqueId

    fun getName(): String = "LuminousLocal"
    fun format(amount: Double): String = "%,d coins".format(amount.toLong())

    fun hasAccount(uuid: UUID): Boolean = balances.containsKey(uuid)
    fun createAccount(uuid: UUID) { balances.putIfAbsent(uuid, 0.0); persist(uuid) }

    fun getBalance(uuid: UUID): Double = balances.getOrDefault(uuid, 0.0)
    fun has(player: OfflinePlayer, amount: Double): Boolean = getBalance(key(player)) >= amount
    fun has(uuid: UUID, amount: Double): Boolean = getBalance(uuid) >= amount

    fun withdrawPlayer(p: OfflinePlayer, amount: Double): Boolean {
        val uuid = key(p)
        val cur = getBalance(uuid)
        if (cur < amount) return false
        balances[uuid] = cur - amount
        persist(uuid)
        return true
    }

    fun depositPlayer(p: OfflinePlayer, amount: Double): Boolean {
        val uuid = key(p)
        balances[uuid] = getBalance(uuid) + amount
        persist(uuid)
        return true
    }

    private fun persist(uuid: UUID) {
        val bal = balances.getOrDefault(uuid, 0.0)
        plugin.pluginScope.launch {
            plugin.databaseManager.saveBalance(uuid.toString(), bal)
        }
    }

    fun loadAll() {
        runCatching {
            plugin.databaseManager.loadBalancesAsync().forEach { (u, b) -> balances[UUID.fromString(u)] = b }
        }
    }

    companion object {
        fun install(plugin: RasberryClient): LocalEconomy {
            val local = LocalEconomy(plugin)
            local.loadAll()
            plugin.logger.info("Local economy installed (LuminousLocal) - balances persist in the panel database.")
            return local
        }
    }
}
