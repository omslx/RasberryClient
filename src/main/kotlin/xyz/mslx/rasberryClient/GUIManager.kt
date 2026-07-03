package xyz.mslx.rasberryClient


import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import xyz.mslx.rasberryClient.RasberryClient
import xyz.mslx.rasberryClient.model.SMPData
import xyz.mslx.rasberryClient.model.SMPStatus
import xyz.mslx.rasberryClient.model.WorldType
import java.util.UUID

class GUIManager(private val plugin: RasberryClient) {
    private val mm = MiniMessage.miniMessage()

    fun openMainMenu(player: Player) {
        plugin.pluginScope.launch {
            val smpData = plugin.smpManager.loadPlayerData(player.uniqueId)

            val inv: Inventory = Bukkit.createInventory(null, 27, mm.deserialize("<bold><gold>SMP Network Portal</gold></bold>"))

            val grayGlass = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                itemMeta = itemMeta?.apply { displayName(mm.deserialize(" ")) }
            }
            for (i in 0 until 27) inv.setItem(i, grayGlass)

            if (smpData == null) {
                val createItem = ItemStack(Material.GRASS_BLOCK).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(mm.deserialize("<green><bold>Create Your SMP Server</bold></green>"))
                        lore(listOf(
                            mm.deserialize("<gray>Start your survival world instantly!</gray>"),
                            mm.deserialize("<yellow>Cost: 5,000 Coins</yellow>")
                        ))
                    }
                }
                inv.setItem(13, createItem)
            } else {
                val statusMaterial = when(smpData.status) {
                    SMPStatus.ONLINE -> Material.EMERALD_BLOCK
                    SMPStatus.STARTING -> Material.GOLD_BLOCK
                    SMPStatus.OFFLINE -> Material.REDSTONE_BLOCK
                }

                val serverCard = ItemStack(statusMaterial).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(mm.deserialize("<aqua><bold>${smpData.smpName}</bold></aqua>"))
                        lore(listOf(
                            mm.deserialize("<gray>Version: <yellow>${smpData.serverVersion}</yellow></gray>"),
                            mm.deserialize("<gray>Status: ${formatStatus(smpData.status)}</gray>"),
                            mm.deserialize(""),
                            mm.deserialize("<green>[Left-Click]</green> <white>to Connect</white>"),
                            mm.deserialize("<yellow>[Right-Click]</yellow> <white>to Server Settings</white>")
                        ))
                    }
                }
                inv.setItem(11, serverCard)

                val deleteItem = ItemStack(Material.BARRIER).apply {
                    itemMeta = itemMeta?.apply {
                        displayName(mm.deserialize("<red><bold>Delete SMP Server</bold></red>"))
                        lore(listOf(mm.deserialize("<gray>Danger zone! Permanent action.</gray>")))
                    }
                }
                inv.setItem(15, deleteItem)
            }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                player.openInventory(inv)
            })
        }
    }

    fun openCreationMenu(player: Player, selectedVersion: String = "1.21", hardcore: Boolean = false, worldType: WorldType = WorldType.DEFAULT) {
        val inv = Bukkit.createInventory(null, 36, mm.deserialize("<green>Customize Your Server</green>"))
        val mm = MiniMessage.miniMessage()

        val versionItem = ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(mm.deserialize("<yellow>Select Version</yellow>"))
                lore(listOf(mm.deserialize("<gray>Current: <aqua>$selectedVersion</aqua></gray>")))
            }
        }
        inv.setItem(10, versionItem)

        val hcMaterial = if (hardcore) Material.LEVER else Material.REDSTONE_TORCH
        val hcItem = ItemStack(hcMaterial).apply {
            itemMeta = itemMeta?.apply {
                displayName(mm.deserialize("<red>Hardcore Mode</red>"))
                lore(listOf(mm.deserialize("<gray>Status: ${if(hardcore) "<green>Enabled</green>" else "<red>Disabled</red>"}</gray>")))
            }
        }
        inv.setItem(12, hcItem)

        val worldItem = ItemStack(Material.MAP).apply {
            itemMeta = itemMeta?.apply {
                displayName(mm.deserialize("<yellow>World Type</yellow>"))
                lore(listOf(mm.deserialize("<gray>Type: <aqua>${worldType.name}</aqua></gray>")))
            }
        }
        inv.setItem(14, worldItem)

        val confirmItem = ItemStack(Material.SLIME_BALL).apply {
            itemMeta = itemMeta?.apply {
                displayName(mm.deserialize("<green><bold>Confirm & Launch Server</bold></green>"))
            }
        }
        inv.setItem(22, confirmItem)

        player.openInventory(inv)
    }

    fun openConfirmationMenu(player: Player) {
        val inv = Bukkit.createInventory(null, 27, mm.deserialize("<red>Are you absolutely sure?</red>"))
        val yesItem = ItemStack(Material.GREEN_WOOL).apply { itemMeta = itemMeta?.apply { displayName(mm.deserialize("<green>Confirm Delete</green>")) } }
        val noItem = ItemStack(Material.RED_WOOL).apply { itemMeta = itemMeta?.apply { displayName(mm.deserialize("<red>Cancel</red>")) } }

        inv.setItem(11, yesItem)
        inv.setItem(15, noItem)
        player.openInventory(inv)
    }

    private fun formatStatus(status: SMPStatus): String = when(status) {
        SMPStatus.ONLINE -> "<green>ONLINE</green>"
        SMPStatus.OFFLINE -> "<red>OFFLINE</red>"
        SMPStatus.STARTING -> "<yellow>STARTING...</yellow>"
    }
}