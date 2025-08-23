package org.hn.wanderingplus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import org.hn.wanderingplus.command.CommandRegistry
import org.hn.wanderingplus.command.sub.InfoCommand
import org.hn.wanderingplus.command.sub.SpawnCommand
import org.hn.wanderingplus.listener.PlayerListener
import org.hn.wanderingplus.listener.TraderListener

class WanderingPlus: JavaPlugin() {

    companion object {
        lateinit var instance: WanderingPlus
            private set
    }

    override fun onEnable() {
        instance = this
        TraderSpawnManager.start(this)
        CommandRegistry
            .register(SpawnCommand())
            .register(InfoCommand())

        server.pluginManager.registerEvents(PlayerListener(), this)
        server.pluginManager.registerEvents(TraderListener(), this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(CommandRegistry.buildRoot())
        }
    }

    override fun onDisable() {
        TraderSpawnManager.stop()
    }
}