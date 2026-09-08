package xyz.mslx.rasberryClient.economy

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.ServicePriority
import xyz.mslx.rasberryClient.RasberryClient
import java.util.UUID
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Local (built-in) economy provider - no external economy plugin required.
 * Balances persist in the panel database (smp_economy table).
 * If another Vault economy provider is already registered, it wins and this
 * class is not installed.
 */
class LocalEconomy(private val plugin: RasberryClient) : Economy {

    private val balances = ConcurrentHashMap<UUID, Double>()

    private fun uuidOf(name: String): UUID = Bukkit.getOfflinePlayer(name).uniqueId
    private fun key(p: OfflinePlayer): UUID = p.uniqueId

    override fun isEnabled(): Boolean = plugin.isEnabled
    override fun getName(): String = "LuminousLocal"
    override fun hasBankSupport(): Boolean = false
    override fun fractionalDigits(): Int = 0
    override fun format(amount: Double): String = "%,d coins".format(amount.toLong())
    override fun currencyNamePlural(): String = "coins"
    override fun currencyNameSingular(): String = "coin"

    override fun hasAccount(player: OfflinePlayer): Boolean = balances.containsKey(key(player))
    override fun hasAccount(playerName: String): Boolean = hasAccount(uuidOf(playerName))
    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean = hasAccount(player)
    override fun hasAccount(playerName: String, worldName: String): Boolean = hasAccount(playerName)

    override fun createPlayerAccount(player: OfflinePlayer): Boolean {
        balances.putIfAbsent(key(player), 0.0)
        persist(key(player))
        return true
    }
    override fun createPlayerAccount(playerName: String): Boolean {
        createPlayerAccount(uuidOf(playerName)); return true
    }
    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean = createPlayerAccount(player)
    override fun createPlayerAccount(playerName: String, worldName: String): Boolean = createPlayerAccount(playerName)

    override fun getBalance(player: OfflinePlayer): Double = balances.getOrDefault(key(player), 0.0)
    override fun getBalance(playerName: String): Double = getBalance(uuidOf(playerName))
    override fun getBalance(player: OfflinePlayer, worldName: String): Double = getBalance(player)
    override fun getBalance(playerName: String, worldName: String): Double = getBalance(playerName)

    override fun has(player: OfflinePlayer, amount: Double): Boolean = getBalance(player) >= amount
    override fun has(playerName: String, amount: Double): Boolean = has(uuidOf(playerName), amount)
    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean = has(player, amount)
    override fun has(playerName: String, worldName: String, amount: Double): Boolean = has(playerName, amount)

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw a negative amount")
        val cur = getBalance(player)
        if (cur < amount) return EconomyResponse(0.0, cur, EconomyResponse.ResponseType.FAILURE, "Insufficient funds")
        val newBal = cur - amount
        balances[key(player)] = newBal
        persist(key(player))
        return EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null)
    }
    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse = withdrawPlayer(uuidOf(playerName), amount)
    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse = withdrawPlayer(player, amount)
    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse = withdrawPlayer(playerName, amount)

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit a negative amount")
        val newBal = getBalance(player) + amount
        balances[key(player)] = newBal
        persist(key(player))
        return EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null)
    }
    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse = depositPlayer(uuidOf(playerName), amount)
    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse = depositPlayer(player, amount)
    override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse = depositPlayer(playerName, amount)

    override fun createBank(bankName: String, owner: OfflinePlayer): EconomyResponse = notImpl()
    override fun createBank(bankName: String, ownerName: String): EconomyResponse = notImpl()
    override fun deleteBank(bankName: String): EconomyResponse = notImpl()
    override fun bankBalance(bankName: String): EconomyResponse = notImpl()
    override fun bankHas(bankName: String, amount: Double): EconomyResponse = notImpl()
    override fun bankWithdraw(bankName: String, amount: Double): EconomyResponse = notImpl()
    override fun bankDeposit(bankName: String, amount: Double): EconomyResponse = notImpl()
    override fun isBankOwner(bankName: String, ownerName: String): EconomyResponse = notImpl()
    override fun isBankOwner(bankName: String, owner: OfflinePlayer): EconomyResponse = notImpl()
    override fun isBankMember(bankName: String, memberName: String): EconomyResponse = notImpl()
    override fun isBankMember(bankName: String, member: OfflinePlayer): EconomyResponse = notImpl()
    override fun getBanks(): MutableList<String> = mutableListOf()

    private fun notImpl(): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported")



    // UUID-based internal ops (String overloads resolve name -> UUID then delegate here)
    private fun hasAccount(uuid: UUID): Boolean = balances.containsKey(uuid)
    private fun createPlayerAccount(uuid: UUID): Boolean {
        balances.putIfAbsent(uuid, 0.0)
        persist(uuid)
        return true
    }
    private fun getBalance(uuid: UUID): Double = balances.getOrDefault(uuid, 0.0)
    private fun has(uuid: UUID, amount: Double): Boolean = getBalance(uuid) >= amount
    private fun withdrawPlayer(uuid: UUID, amount: Double): EconomyResponse {
        if (amount < 0) return EconomyResponse(0.0, getBalance(uuid), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw a negative amount")
        val cur = getBalance(uuid)
        if (cur < amount) return EconomyResponse(0.0, cur, EconomyResponse.ResponseType.FAILURE, "Insufficient funds")
        val newBal = cur - amount
        balances[uuid] = newBal
        persist(uuid)
        return EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null)
    }
    private fun depositPlayer(uuid: UUID, amount: Double): EconomyResponse {
        if (amount < 0) return EconomyResponse(0.0, getBalance(uuid), EconomyResponse.ResponseType.FAILURE, "Cannot deposit a negative amount")
        val newBal = getBalance(uuid) + amount
        balances[uuid] = newBal
        persist(uuid)
        return EconomyResponse(amount, newBal, EconomyResponse.ResponseType.SUCCESS, null)
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
        /** Install the economy: prefer an existing Vault provider; otherwise register LuminousLocal. */
        fun install(plugin: RasberryClient): LocalEconomy? {
            val existing = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider
            if (existing != null) {
                plugin.logger.info("External Vault economy found: ${existing.name} - using it.")
                return null
            }
            val local = LocalEconomy(plugin)
            Bukkit.getServicesManager().register(Economy::class.java, local, plugin, ServicePriority.Normal)
            plugin.logger.info("Local economy installed (LuminousLocal) - balances persist in the panel database.")
            return local
        }
    }
}
