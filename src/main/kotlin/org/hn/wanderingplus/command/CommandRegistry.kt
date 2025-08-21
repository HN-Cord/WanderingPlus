package org.hn.wanderingplus.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

object CommandRegistry {
    private val subs = mutableListOf<SubCommand>()

    fun register(sub: SubCommand) {
        subs.add(sub)
    }

    internal fun buildRoot(): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal("wanderingplus")
        subs.forEach { sub ->
            sub.permission?.let { root.requires { src -> src.sender.hasPermission(it) }

            root.then(sub.build()) }
        }
        return root.build()
    }
}