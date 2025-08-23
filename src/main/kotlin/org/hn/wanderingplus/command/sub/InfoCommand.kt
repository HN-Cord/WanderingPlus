package org.hn.wanderingplus.command.sub

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.hn.wanderingplus.TraderSpawnManager
import org.hn.wanderingplus.command.SubCommand

class InfoCommand: SubCommand {
    override val name: String = "info"
    override val permission: String? = null

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .executes { ctx -> executeSelf(ctx) }
            .then(
                Commands.argument("player", ArgumentTypes.player())
                    .requires { src -> src.sender.hasPermission("command.wanderingplus.admin.info.others") }
                    .executes { ctx -> executeOthers(ctx) }
            )
    }

    private fun executeSelf(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        if (sender !is Player) {
            sender.sendMessage(Component.text("此指令只能由玩家使用").color(NamedTextColor.RED))
            return 0
        }

        showPlayerInfo(sender, sender)
        return 1
    }

    private fun executeOthers(ctx: CommandContext<CommandSourceStack>): Int {
        val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
        val targets: Collection<Player> = resolver.resolve(ctx.source)

        if (targets.isEmpty()) {
            ctx.source.sender.sendMessage(Component.text("找不到目標玩家").color(NamedTextColor.RED))
            return 0
        }

        for (target in targets) {
            showPlayerInfo(ctx.source.sender, target)
        }
        return targets.size
    }

    private fun showPlayerInfo(sender: org.bukkit.command.CommandSender, target: Player) {
        val info = TraderSpawnManager.getPlayerInfo(target.uniqueId)

        sender.sendMessage(
            Component.text("=== ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("流浪商人資訊").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text(" - ").color(NamedTextColor.GOLD))
                .append(Component.text(target.name).color(NamedTextColor.WHITE))
                .append(Component.text(" ===").color(NamedTextColor.GOLD))
        )

        // 是否已生成流浪商人
        val hasTraderText = if (info.hasTrader) {
            Component.text("✅ 是").color(NamedTextColor.GREEN)
        } else {
            Component.text("❌ 否").color(NamedTextColor.RED)
        }

        sender.sendMessage(
            Component.text("已生成流浪商人：").color(NamedTextColor.AQUA)
                .append(hasTraderText)
        )

        if (info.hasTrader) {
            // 如果已生成，顯示隊列位置
            sender.sendMessage(
                Component.text("當前狀態：").color(NamedTextColor.AQUA)
                    .append(Component.text("商人已生成，等待消失").color(NamedTextColor.YELLOW))
            )

            info.queuePosition?.let { position ->
                sender.sendMessage(
                    Component.text("重置順序：").color(NamedTextColor.AQUA)
                        .append(Component.text("第 ").color(NamedTextColor.WHITE))
                        .append(Component.text("$position").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        .append(Component.text(" 位").color(NamedTextColor.WHITE))
                        .append(Component.text(" (共 ${info.totalSummoned} 位玩家有商人)").color(NamedTextColor.GRAY))
                )
            }
        } else {
            // 如果未生成，顯示當前機率
            sender.sendMessage(
                Component.text("當前生成機率：").color(NamedTextColor.AQUA)
                    .append(Component.text("${String.format("%.1f", info.currentChance)}%").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                    .append(Component.text(" (階段 ${info.currentStage + 1}/${info.maxStage})").color(NamedTextColor.GRAY))
            )

            sender.sendMessage(
                Component.text("下次檢查：").color(NamedTextColor.AQUA)
                    .append(Component.text("每20分鐘自動檢查一次").color(NamedTextColor.WHITE))
            )

            // 顯示機率階段說明
            sender.sendMessage(
                Component.text("階段說明：").color(NamedTextColor.AQUA)
                    .append(Component.text("每次失敗會提高下次的生成機率").color(NamedTextColor.GRAY))
            )
        }

        // 分隔線
        sender.sendMessage(
            Component.text("==========================================").color(NamedTextColor.GOLD)
        )
    }


}