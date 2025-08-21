package org.hn.wanderingplus.command.sub

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.entity.Player
import org.hn.wanderingplus.TraderSpawnManager
import org.hn.wanderingplus.command.SubCommand

class SpawnCommand: SubCommand {
    override val name: String = "spawn"
    override val permission: String = "command.wanderingplus.admin.spawn"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .then(
                Commands.argument("player", ArgumentTypes.player())
                    .executes { ctx -> execute(ctx) }
            )
    }

    private fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
        val targets: Collection<Player> = resolver.resolve(ctx.source)

        if (targets.isEmpty()) {
            ctx.source.sender.sendRichMessage("<red>找不到目標玩家")
            return 0
        }

        var success = 0
        for (p in targets) {
            val ok = TraderSpawnManager.forceSummonFor(p.uniqueId)
            if (ok) {
                success++
                ctx.source.sender.sendRichMessage("<green>已在 <yellow>${p.name}</yellow> 的位置生成流浪商人")
            } else {
                ctx.source.sender.sendRichMessage("<red>為 <yellow>${p.name}</yellow> 生成失敗：可能已召喚過、達上限或無合法落點")
            }
        }
        return success
    }
}