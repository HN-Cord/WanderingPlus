package org.hn.wanderingplus.listener

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.hn.wanderingplus.TraderSpawnManager
import org.hn.wanderingplus.WanderingPlus

class TraderListener: Listener {

    @EventHandler
    fun onTraderRemove(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity
        if (entity.type != org.bukkit.entity.EntityType.WANDERING_TRADER) return

        val summoned = TraderSpawnManager.getSummonedPlayers()
        if (summoned.isEmpty()) return

        val randomUUID = summoned.first()
        TraderSpawnManager.onTraderDespawn(randomUUID)
        WanderingPlus.instance.logger.info("Restarted trader for $randomUUID")
    }
}