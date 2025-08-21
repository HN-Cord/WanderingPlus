package org.hn.wanderingplus.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack

interface SubCommand {
    val name: String
    val permission: String?

    fun build(): LiteralArgumentBuilder<CommandSourceStack>
}