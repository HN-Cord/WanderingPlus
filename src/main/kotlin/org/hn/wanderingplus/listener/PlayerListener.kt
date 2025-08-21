package org.hn.wanderingplus.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.hn.wanderingplus.TraderSpawnManager

class PlayerListener: Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        TraderSpawnManager.onPlayerJoin(id)
    }
}