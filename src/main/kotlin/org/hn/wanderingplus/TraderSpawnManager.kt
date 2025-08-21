package org.hn.wanderingplus

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

object TraderSpawnManager {
    private const val PERIOD_TICKS: Long = 20L * 60L * 20L
    private val CHANCES = doubleArrayOf(0.25, 0.50, 1.00)

    private val attemptsByPlayer = mutableMapOf<UUID, Int>()
    private val summonedPlayers = mutableSetOf<UUID>()

    private var taskId: Int = -1

    fun start(plugin: JavaPlugin) {
        if (taskId != -1) return
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            ::tick,
            20L,
            PERIOD_TICKS
        )
    }

    fun stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId)
            taskId = -1
        }
        attemptsByPlayer.clear()
        summonedPlayers.clear()
    }

    fun resetSummonedFilter() {
        summonedPlayers.clear()
        attemptsByPlayer.clear()
    }

    private fun tick() {
        if (!underGlobalCap()) return

        val candidates = Bukkit.getOnlinePlayers()
            .filter { it.uniqueId !in summonedPlayers }
            .shuffled()

        for (player in candidates) {
            val id = player.uniqueId
            val stage = attemptsByPlayer.getOrDefault(id, 0).coerceIn(0, CHANCES.lastIndex)
            val chance = CHANCES[stage]
            val roll = Random.nextDouble()

            if (roll <= chance) {
                val loc = pickSpawnLocationNear(player)
                if (loc == null) {
                    // 找不到地點也視為失敗
                    attemptsByPlayer[id] = (stage + 1).coerceAtMost(CHANCES.lastIndex)
                    continue
                }
                val ok = spawnVanillaTrader(loc)
                if (ok) {
                    summonedPlayers.add(id)
                    attemptsByPlayer.remove(id)
                    WanderingPlus.instance.logger.info("Trader spawned at ${loc.x}, ${loc.y}, ${loc.z} for ${player.name}")
                } else {
                    // 無法生成 也視為失敗
                    attemptsByPlayer[id] = (stage + 1).coerceAtMost(CHANCES.lastIndex)
                }
            } else {
                // 直接失敗
                attemptsByPlayer[id] = (stage + 1).coerceAtMost(CHANCES.lastIndex)
            }
        }
    }

    // 玩家上線事件
    fun onPlayerJoin(id: UUID) {
        if (summonedPlayers.contains(id)) return
        attemptsByPlayer.putIfAbsent(id, 1)
    }

    // 強制召喚 遵循標記
    fun forceSummonFor(
        id: UUID,
    ): Boolean {
        val player = Bukkit.getPlayer(id) ?: return false
        if (summonedPlayers.contains(id)) return false

        val loc = pickSpawnLocationNear(player) ?: return false
        val ok = spawnVanillaTrader(loc)
        if (ok) {
            summonedPlayers.add(id)
            attemptsByPlayer.remove(id)
        }

        return ok
    }

    fun restart(id: UUID) {
        if (summonedPlayers.contains(id)) {
            summonedPlayers.remove(id)
            attemptsByPlayer.putIfAbsent(id, 1)
        }
    }

    fun getSummonedPlayers() = summonedPlayers.toList()
}