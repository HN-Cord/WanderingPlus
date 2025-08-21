package org.hn.wanderingplus

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.horse.TraderLlama
import net.minecraft.world.entity.npc.WanderingTrader
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun spawnVanillaTrader(loc: Location): Boolean {
    val level = (loc.world as CraftWorld).handle as ServerLevel

    val trader = WanderingTrader(EntityType.WANDERING_TRADER, level)
    trader.setPos(loc.x, loc.y, loc.z)
    trader.despawnDelay = 48_000

    if (!level.addFreshEntity(trader)) return false

    val radius = 2.0
    val baseYawRad = Math.toRadians(loc.yaw.toDouble())

    fun spawnLlama(offsetAngleRad: Double) {
        val dx = radius * cos(baseYawRad + offsetAngleRad)
        val dz = radius * sin(baseYawRad + offsetAngleRad)

        val llama = TraderLlama(EntityType.TRADER_LLAMA, level)
        llama.setPos(loc.x + dx, loc.y, loc.z + dz)
        llama.yRot = loc.yaw
        llama.xRot = 0f

        if (level.addFreshEntity(llama)) {
            llama.setLeashedTo(trader, true)
            llama.setDespawnDelay(trader.despawnDelay - 1)
        }
    }

    spawnLlama(+Math.PI / 4)
    spawnLlama(-Math.PI / 4)
    return true
}

fun pickSpawnLocationNear(player: Player): Location? {
    val world = player.world
    repeat(8) {
        val r = Random.nextInt(16, 33)
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val dx = (r * cos(angle)).toInt()
        val dz = (r * sin(angle)).toInt()
        val x = player.location.blockX + dx
        val z = player.location.blockZ + dz

        val y = world.getHighestBlockYAt(x, z)
        val loc = Location(world, x + 0.5, y.toDouble() + 1, z + 0.5, player.location.yaw, 0f)
        return loc
    }
    return null
}

fun underGlobalCap(): Boolean {
    val cap = Bukkit.getOnlinePlayers().size
    if (cap <= 0) return false

    val current = Bukkit.getWorlds().sumOf { world ->
        world.entities.count { it.type == org.bukkit.entity.EntityType.WANDERING_TRADER}
    }
    return current < cap
}