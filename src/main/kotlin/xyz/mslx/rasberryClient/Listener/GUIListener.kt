package xyz.mslx.rasberryClient.Listener


import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.mslx.rasberryClient.RasberryClient
import xyz.mslx.rasberryClient.model.SMPData
import xyz.mslx.rasberryClient.model.SMPStatus
import java.util.UUID

class GUIListener(private val plugin: RasberryClient) : Listener {
    private val mm = MiniMessage.miniMessage()
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = event.view.title()

        val titleString = MiniMessage.miniMessage().serialize(title)

        if (titleString.contains("SMP Network Portal") || titleString.contains("Customize Your Server") || titleString.contains("Are you absolutely sure")) {
            event.isCancelled = true
            val clickedItem = event.currentItem ?: return
            if (clickedItem.type == Material.AIR) return

            if (titleString.contains("SMP Network Portal")) {
                if (clickedItem.type == Material.GRASS_BLOCK) {
                    plugin.guiManager.openCreationMenu(player)
                } else if (clickedItem.type == Material.EMERALD_BLOCK || clickedItem.type == Material.REDSTONE_BLOCK || clickedItem.type == Material.GOLD_BLOCK) {
                    val smpData = plugin.smpManager.getCachedData(player.uniqueId) ?: return
                    if (event.click.isLeftClick) {
                        connectToSmp(player, smpData)
                    } else if (event.click.isRightClick) {
                        player.sendMessage(mm.deserialize("<yellow>Opening Server Settings...</yellow>"))
                    }
                } else if (clickedItem.type == Material.BARRIER) {
                    plugin.guiManager.openConfirmationMenu(player)
                }
            }

            else if (titleString.contains("Are you absolutely sure")) {
                if (clickedItem.type == Material.GREEN_WOOL) {
                    plugin.smpManager.deleteRequest(player.uniqueId.toString())
                    player.sendMessage(mm.deserialize("<red>Your SMP server deletion request has been sent.</red>"))
                    player.closeInventory()
                } else if (clickedItem.type == Material.RED_WOOL) {
                    plugin.guiManager.openMainMenu(player)
                }
            }

            else if (titleString.contains("Customize Your Server")) {
                if (clickedItem.type == Material.SLIME_BALL) {
                    val econ = plugin.luminousEconomy ?: plugin.economy
                    if (econ == null) {
                        player.sendMessage(mm.deserialize("<red>Economy system not ready - try again in a moment.</red>"))
                        return
                    }

                    val cost = 5000.0
                    if (econ.has(player, cost)) {
                        econ.withdrawPlayer(player, cost)
                        player.sendMessage(mm.deserialize("<green>5,000 coins deducted successfully!</green>"))

                        val newSmp = SMPData(
                            ownerUuid = player.uniqueId.toString(),
                            smpId = UUID.randomUUID().toString(),
                            smpName = "${player.name}'s SMP",
                            serverVersion = "1.21",
                            status = SMPStatus.STARTING
                        )

                        plugin.smpManager.createRequest(newSmp)
                        player.sendMessage(mm.deserialize("<gold>Deploying your new Multi-Version SMP... Please wait.</gold>"))
                        player.closeInventory()
                    } else {
                        player.sendMessage(mm.deserialize("<red>Insufficient funds! You need 5,000 coins to create an SMP server.</red>"))
                        player.closeInventory()
                    }
                }
            }
        }
    }
    private fun connectToSmp(player: Player, smpData: SMPData) {
        if (smpData.status != SMPStatus.ONLINE) {
            player.sendMessage(mm.deserialize("<red>Your server is currently offline or starting!</red>"))
            return
        }
        val ipPort = smpData.serverIpPort.trim()
        if (ipPort.isEmpty()) {
            player.sendMessage(mm.deserialize("<red>Server address is not ready yet — try again in a moment.</red>"))
            return
        }
        val parts = ipPort.split(":")
        val host = parts.getOrNull(0)
        val port = parts.getOrNull(1)?.toIntOrNull()
        if (host.isNullOrBlank() || port == null || port !in 1..65535) {
            player.sendMessage(mm.deserialize("<yellow>Connect manually: <white>$ipPort</white></yellow>"))
            return
        }

        try {
            player.transfer(host, port)
        } catch (e: Exception) {
            plugin.logger.warning("transfer to $ipPort failed for ${player.name}: ${e.message}")
            player.sendMessage(mm.deserialize("<yellow>Transfer failed — connect manually: <white>$ipPort</white></yellow>"))
        }
    }
}