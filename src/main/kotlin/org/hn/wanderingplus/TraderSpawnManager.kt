package org.hn.wanderingplus

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Leashable
import net.minecraft.world.entity.animal.horse.Llama
import net.minecraft.world.entity.animal.horse.TraderLlama
import net.minecraft.world.entity.npc.WanderingTrader
import org.bukkit.Bukkit
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.hn.wanderingplus.model.PlayerTraderInfo
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object TraderSpawnManager {
    private const val PERIOD_TICKS: Long = 20L * 60L * 20L
    private val CHANCES = doubleArrayOf(0.02, 0.045, 0.07, 0.095, 0.12, 0.145, 0.17, 0.195, 0.22, 0.25)

    private val attemptsByPlayer = mutableMapOf<UUID, Int>()
    private val summonedPlayers = mutableSetOf<UUID>()
    private val lastAttemptTime = mutableMapOf<UUID, Long>()

    private var taskId: Int = -1

    fun start(plugin: JavaPlugin) {
        if (taskId != -1) return
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            ::tick,
            20L * 60L, // 每分鐘檢查
            20L * 60L  // 每分鐘檢查
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

    private fun tick() {
        val currentTime = System.currentTimeMillis()

        val candidates = Bukkit.getOnlinePlayers()
            .filter { player ->
                val id = player.uniqueId
                // 檢查是否已有商人
                if (id in summonedPlayers) return@filter false

                // 檢查是否到了嘗試時間（20分鐘）
                val lastTime = lastAttemptTime[id] ?: 0
                (currentTime - lastTime) >= (PERIOD_TICKS * 50) // 轉換為毫秒
            }

        for (player in candidates) {
            val id = player.uniqueId
            lastAttemptTime[id] = currentTime

            val stage = attemptsByPlayer.getOrDefault(id, 0).coerceIn(0, CHANCES.lastIndex)
            val chance = CHANCES[stage]
            val roll = Random.nextDouble()

            if (roll <= chance) {
                val loc = pickTraderSpawnLikeVanilla(player, attempts = 10, minEuclidDistance = 24)
                if (loc == null) {
                    attemptsByPlayer[id] = (stage + 1).coerceAtMost(CHANCES.lastIndex)
                    continue
                }
                val ok = spawnTraderWithLlamas(loc)
                if (ok) {
                    summonedPlayers.add(id)
                    attemptsByPlayer.remove(id)
                    WanderingPlus.instance.logger.info("Trader spawned at ${loc.x}, ${loc.y}, ${loc.z} for ${player.name}")
                } else {
                    attemptsByPlayer[id] = (stage + 1).coerceAtMost(CHANCES.lastIndex)
                }
            } else {
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
    fun forceSummonFor(id: UUID): Boolean {
        val player = Bukkit.getPlayer(id) ?: return false
        if (summonedPlayers.contains(id)) return false

        val loc = pickTraderSpawnLikeVanilla(player, attempts = 10, minEuclidDistance = 0) ?: return false
        val ok = spawnTraderWithLlamas(loc)
        if (ok) {
            summonedPlayers.add(id)
            attemptsByPlayer.remove(id)
        }
        return ok
    }

    fun onTraderDespawn(id: UUID) {
        if (summonedPlayers.contains(id)) {
            summonedPlayers.remove(id)
            attemptsByPlayer[id] = 0
            lastAttemptTime[id] = System.currentTimeMillis()
        }
    }


    fun getSummonedPlayers() = summonedPlayers.toList()

    fun getPlayerInfo(playerId: UUID): PlayerTraderInfo {
        val hasTrader = playerId in summonedPlayers
        val currentStage = attemptsByPlayer.getOrDefault(playerId, 0).coerceIn(0, CHANCES.lastIndex)
        val currentChance = CHANCES[currentStage] * 100 // 轉換為百分比

        val queuePosition = if (hasTrader) {
            // 如果已有商人，計算在重置隊列中的位置
            val summonedList = summonedPlayers.toList()
            summonedList.indexOf(playerId) + 1
        } else null

        return PlayerTraderInfo(
            hasTrader = hasTrader,
            currentChance = currentChance,
            currentStage = currentStage,
            maxStage = CHANCES.size,
            queuePosition = queuePosition,
            totalSummoned = summonedPlayers.size
        )
    }

    // 位置判斷


    private fun pickTraderSpawnLikeVanilla(
        player: Player,
        attempts: Int = 10,
        minEuclidDistance: Int = 24
    ): Location? {
        val world = player.world
        if (world.environment != World.Environment.NORMAL) return null

        val base = player.location
        repeat(attempts) {
            val dx = Random.nextInt(-48, 49)
            val dz = Random.nextInt(-48, 49)
            val x = base.blockX + dx
            val z = base.blockZ + dz

            val dist2 = (x - base.blockX) * (x - base.blockX) + (z - base.blockZ) * (z - base.blockZ)
            if (dist2 < minEuclidDistance * minEuclidDistance) return@repeat

            ensureChunkLoaded(world, x, z)

            val groundY0 = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES)
            if (groundY0 <= world.minHeight) return@repeat

            val standY = findStandableYWithFallback(world, x, z, groundY0, fallbackDepth = 2) ?: return@repeat
            val spawnY = standY + 1

            if (!hasTwoBlocksHeadroom(world, x, spawnY, z)) return@repeat
            if (isDangerous(world.getBlockAt(x, standY, z))) return@repeat

            // 檢查光照等級（流浪商人需要在光照較好的地方生成）
            val lightLevel = world.getBlockAt(x, spawnY, z).lightLevel
            if (lightLevel < 4) return@repeat

            val yaw = Random.nextFloat() * 360f - 180f
            return Location(world, x + 0.5, spawnY.toDouble(), z + 0.5, yaw, 0f)
        }
        return null
    }




    // 生成 流浪商人


    private fun spawnTraderWithLlamas(loc: Location): Boolean {
        val bukkitWorld = loc.world ?: return false
        if (bukkitWorld.environment != World.Environment.NORMAL) return false

        val level: ServerLevel = (bukkitWorld as CraftWorld).handle
        ensureChunkLoaded(bukkitWorld, loc.blockX, loc.blockZ)

        // 創建並生成流浪商人
        val trader = WanderingTrader(net.minecraft.world.entity.EntityType.WANDERING_TRADER, level)
        trader.setPos(loc.x, loc.y, loc.z)
        trader.despawnDelay = 48_000

        if (!level.addFreshEntity(trader)) {
            return false
        }

        val spawnedLlamas = mutableListOf<TraderLlama>()

        // 嘗試多次找到羊駝位置
        repeat(15) { // 增加到15次嘗試，提高成功率
            if (spawnedLlamas.size >= 2) return@repeat // 已經生成足夠的羊駝

            // 隨機選擇角度和距離
            val angle = Random.nextDouble() * 2 * Math.PI
            val distance = Random.nextDouble(1.5, 4.0) // 距離1.5-4格，範圍稍大

            val lx = loc.x + distance * cos(angle)
            val lz = loc.z + distance * sin(angle)
            val bx = lx.toInt()
            val bz = lz.toInt()

            ensureChunkLoaded(bukkitWorld, bx, bz)
            val gy0 = bukkitWorld.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES)
            val sy = findStandableYWithFallback(bukkitWorld, bx, bz, gy0, fallbackDepth = 3)?.plus(1) ?: return@repeat

            if (!hasTwoBlocksHeadroom(bukkitWorld, bx, sy, bz)) return@repeat
            if (isDangerous(bukkitWorld.getBlockAt(bx, sy - 1, bz))) return@repeat

            val llama = TraderLlama(net.minecraft.world.entity.EntityType.TRADER_LLAMA, level)
            llama.setPos(bx + 0.5, sy.toDouble(), bz + 0.5)

            if (level.addFreshEntity(llama)) {
                spawnedLlamas.add(llama)
                (llama as Leashable).setLeashedTo(trader, true)
            }
        }

        return true // 總是返回成功，只要商人生成了
    }



    // 地面判定 / 工具 / 黑名單


    private fun ensureChunkLoaded(world: World, x: Int, z: Int) {
        val chunk = world.getChunkAt(x shr 4, z shr 4)
        if (!chunk.isLoaded) chunk.load()
    }

    /** 從 heightmap 起點往下 fallbackDepth 格內找第一個可站立頂面。 */
    private fun findStandableYWithFallback(world: World, x: Int, z: Int, startY: Int, fallbackDepth: Int): Int? {
        var y = startY
        repeat(fallbackDepth + 1) {
            val b = world.getBlockAt(x, y, z)
            if (isStandableTop(b)) return y
            y--
            if (y < world.minHeight) return null
        }
        return null
    }

    /** 判斷方塊頂面是否可站（排除下半磚/下半階梯/可變高度頂面/流體/鷹架/光源/鐘乳石尖端/軌道等） */
    private fun isStandableTop(b: Block): Boolean {
        val t = b.type

        // 直接檢查不可站立的方塊類型
        if (t == Material.WATER || t == Material.LAVA ||
            t == Material.LIGHT || t == Material.SCAFFOLDING ||
            t == Material.POINTED_DRIPSTONE) {
            return false
        }

        (b.blockData as? Slab)?.let { slab ->
            return slab.type == Slab.Type.TOP || slab.type == Slab.Type.DOUBLE
        }
        (b.blockData as? Stairs)?.let { stairs ->
            return stairs.half == Bisected.Half.TOP
        }
        if (t == Material.FARMLAND || t == Material.DIRT_PATH) return false
        if (t.name.endsWith("_RAIL")) return false
        return t.isOccluding || t.isSolid
    }


    /** 檢查腳底上方兩格是否可容納實體（空/可穿過、且非流體）。 */
    private fun hasTwoBlocksHeadroom(world: World, spawnX: Int, spawnY: Int, spawnZ: Int): Boolean {
        val b1 = world.getBlockAt(spawnX, spawnY,     spawnZ)
        val b2 = world.getBlockAt(spawnX, spawnY + 1, spawnZ)
        return isEmptyForSpawn(b1) && isEmptyForSpawn(b2)
    }

    private fun isEmptyForSpawn(b: Block): Boolean {
        val t = b.type
        if (t == Material.WATER || t == Material.LAVA) return false
        return b.isEmpty || b.isPassable
    }

    /** 避免站在會傷害/推動/異常的方塊頂。 */
    private fun isDangerous(b: Block): Boolean = when (b.type) {
        Material.CACTUS,
        Material.MAGMA_BLOCK,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.FIRE, Material.SOUL_FIRE,
        Material.SWEET_BERRY_BUSH -> true
        else -> false
    }

    /**
     * 全伺服器目前 Wandering Trader 數量 < 在線玩家數量 才允許生成
     */
    private fun underGlobalCap(): Boolean {
        val totalTraders = Bukkit.getWorlds().sumOf { w ->
            w.entities.count { it.type == EntityType.WANDERING_TRADER }
        }
        // 改為更嚴格的限制：總數不超過在線玩家數的 1/2
        val online = Bukkit.getOnlinePlayers().size
        return totalTraders < (online / 2).coerceAtLeast(1)
    }
}