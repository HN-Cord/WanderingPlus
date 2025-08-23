package org.hn.wanderingplus.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

object CommandRegistry {
    private val subs = mutableListOf<SubCommand>()

    fun register(sub: SubCommand): CommandRegistry {
        subs.add(sub)
        return this
    }

    internal fun buildRoot(): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal("wanderingplus")
        subs.forEach { sub ->
            val subCommand = sub.build()
            // 只有當權限不為 null 時才添加權限檢查
            if (sub.permission != null) {
                subCommand.requires { src -> src.sender.hasPermission(sub.permission!!) }
            }
            root.then(subCommand)

        }
        return root.build()
    }
}