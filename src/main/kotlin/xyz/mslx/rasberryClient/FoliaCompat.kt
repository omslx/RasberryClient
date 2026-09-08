package xyz.mslx.rasberryClient

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Folia-safe scheduling helper.
 *
 * On Folia, the global BukkitScheduler does not exist: entity work must run on
 * the entity's scheduler and region work on the RegionScheduler. On Paper both
 * delegate to the normal scheduler. This keeps the plugin loadable on both.
 *
 * Folia detection: class io.papermc.folia.FoliaScheduler present on the server.
 */
object FoliaCompat {

    val isFolia: Boolean = runCatching {
        Class.forName("io.papermc.folia.FoliaScheduler")
        true
    }.getOrDefault(false)

    /** Run a task for a specific player on the thread that owns that player (Folia entity scheduler / Paper main thread). */
    fun runForPlayer(plugin: Plugin, player: Player, task: Runnable) {
        if (isFolia) {
            val ok = runCatching {
                player.scheduler.run(plugin, { task.run() }, null)
            }.isSuccess
            if (ok) return
            // fall through to next tick retry via global? On Folia global does not exist; give up silently.
            return
        }
        Bukkit.getScheduler().runTask(plugin, task)
    }

    /** Run a task on the next tick somewhere safe for global work (Paper: main thread; Folia: global region). */
    fun runGlobal(plugin: Plugin, task: Runnable) {
        if (isFolia) {
            runCatching {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task)
            }
            return
        }
        Bukkit.getScheduler().runTask(plugin, task)
    }
}
