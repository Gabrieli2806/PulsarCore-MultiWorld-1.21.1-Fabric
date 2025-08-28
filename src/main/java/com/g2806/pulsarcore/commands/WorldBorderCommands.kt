package com.g2806.pulsarcore.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.g2806.pulsarcore.PulsarCore
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text

object WorldBorderCommands {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("pc")
            .then(CommandManager.literal("worldborder")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(CommandManager.literal("center")
                    .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                            .executes(::executeSetCenter)
                        )
                    )
                )
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(1.0, 6.0E7))
                        .executes(::executeSetSize)
                    )
                )
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(-6.0E7, 6.0E7))
                        .executes(::executeAddSize)
                        .then(CommandManager.argument("time", IntegerArgumentType.integer(0))
                            .executes(::executeAddSizeOverTime)
                        )
                    )
                )
                .then(CommandManager.literal("damage")
                    .then(CommandManager.literal("amount")
                        .then(CommandManager.argument("damagePerBlock", DoubleArgumentType.doubleArg(0.0))
                            .executes(::executeSetDamage)
                        )
                    )
                    .then(CommandManager.literal("buffer")
                        .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg(0.0))
                            .executes(::executeSetBuffer)
                        )
                    )
                )
                .then(CommandManager.literal("warning")
                    .then(CommandManager.literal("distance")
                        .then(CommandManager.argument("distance", IntegerArgumentType.integer(0))
                            .executes(::executeSetWarningDistance)
                        )
                    )
                    .then(CommandManager.literal("time")
                        .then(CommandManager.argument("time", IntegerArgumentType.integer(0))
                            .executes(::executeSetWarningTime)
                        )
                    )
                )
                .then(CommandManager.literal("get")
                    .executes(::executeGet)
                )
                .then(CommandManager.literal("refresh")
                    .executes(::executeRefresh)
                )
                .executes(::executeHelp)
            )
        )
    }

    private fun executeSetCenter(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val x = DoubleArgumentType.getDouble(context, "x")
            val z = DoubleArgumentType.getDouble(context, "z")

            world.worldBorder.setCenter(x, z)
            context.source.sendMessage(Text.literal("§aSet the center of the world border to §6$x, $z"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border center", e)
            context.source.sendError(Text.literal("§cFailed to set world border center"))
        }
        return 1
    }

    private fun executeSetSize(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = DoubleArgumentType.getDouble(context, "distance")

            world.worldBorder.setSize(distance)
            context.source.sendMessage(Text.literal("§aSet the world border size to §6${String.format("%.1f", distance)} blocks"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border size", e)
            context.source.sendError(Text.literal("§cFailed to set world border size"))
        }
        return 1
    }

    private fun executeAddSize(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = DoubleArgumentType.getDouble(context, "distance")
            val newSize = world.worldBorder.size + distance

            if (newSize < 1.0 || newSize > 6.0E7) {
                context.source.sendError(Text.literal("§cResulting world border size would be out of bounds"))
                return 0
            }

            world.worldBorder.setSize(newSize)
            context.source.sendMessage(Text.literal("§aChanged world border size by §6${String.format("%.1f", distance)} blocks to §6${String.format("%.1f", newSize)} blocks"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error adding to world border size", e)
            context.source.sendError(Text.literal("§cFailed to change world border size"))
        }
        return 1
    }

    private fun executeAddSizeOverTime(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = DoubleArgumentType.getDouble(context, "distance")
            val time = IntegerArgumentType.getInteger(context, "time")
            val currentSize = world.worldBorder.size
            val newSize = currentSize + distance

            if (newSize < 1.0 || newSize > 6.0E7) {
                context.source.sendError(Text.literal("§cResulting world border size would be out of bounds"))
                return 0
            }

            world.worldBorder.interpolateSize(currentSize, newSize, (time * 1000).toLong())
            context.source.sendMessage(Text.literal("§aChanging world border size from §6${String.format("%.1f", currentSize)} §ato §6${String.format("%.1f", newSize)} blocks over §6$time seconds"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error interpolating world border size", e)
            context.source.sendError(Text.literal("§cFailed to change world border size over time"))
        }
        return 1
    }

    private fun executeSetDamage(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val damage = DoubleArgumentType.getDouble(context, "damagePerBlock")

            world.worldBorder.damagePerBlock = damage
            context.source.sendMessage(Text.literal("§aSet world border damage to §6${String.format("%.1f", damage)} per block per second"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border damage", e)
            context.source.sendError(Text.literal("§cFailed to set world border damage"))
        }
        return 1
    }

    private fun executeSetBuffer(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val buffer = DoubleArgumentType.getDouble(context, "distance")

            world.worldBorder.safeZone = buffer
            context.source.sendMessage(Text.literal("§aSet world border safe zone to §6${String.format("%.1f", buffer)} blocks"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border buffer", e)
            context.source.sendError(Text.literal("§cFailed to set world border safe zone"))
        }
        return 1
    }

    private fun executeSetWarningDistance(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = IntegerArgumentType.getInteger(context, "distance")

            world.worldBorder.warningBlocks = distance
            context.source.sendMessage(Text.literal("§aSet world border warning distance to §6$distance blocks"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border warning distance", e)
            context.source.sendError(Text.literal("§cFailed to set world border warning distance"))
        }
        return 1
    }

    private fun executeSetWarningTime(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val time = IntegerArgumentType.getInteger(context, "time")

            world.worldBorder.warningTime = time
            context.source.sendMessage(Text.literal("§aSet world border warning time to §6$time seconds"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error setting world border warning time", e)
            context.source.sendError(Text.literal("§cFailed to set world border warning time"))
        }
        return 1
    }

    private fun executeGet(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val border = world.worldBorder
            val source = context.source

            source.sendMessage(Text.literal(""))
            source.sendMessage(Text.literal("§6═══════════════ §eWorld Border Info §6═══════════════"))
            source.sendMessage(Text.literal("§6▌ §eWorld: §f${world.registryKey.value}"))
            source.sendMessage(Text.literal("§6▌ §eCenter: §f${String.format("%.1f", border.centerX)}, ${String.format("%.1f", border.centerZ)}"))
            source.sendMessage(Text.literal("§6▌ §eSize: §f${String.format("%.1f", border.size)} blocks"))
            source.sendMessage(Text.literal("§6▌ §eSafe Zone: §f${String.format("%.1f", border.safeZone)} blocks"))
            source.sendMessage(Text.literal("§6▌ §eDamage: §f${String.format("%.2f", border.damagePerBlock)} per block/sec"))
            source.sendMessage(Text.literal("§6▌ §eWarning Distance: §f${border.warningBlocks} blocks"))
            source.sendMessage(Text.literal("§6▌ §eWarning Time: §f${border.warningTime} seconds"))
            source.sendMessage(Text.literal("§6═══════════════════════════════════════════════════"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error getting world border info", e)
            context.source.sendError(Text.literal("§cFailed to get world border information"))
        }
        return 1
    }

    private fun executeHelp(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        val source = context.source
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6═══════════════ §ePulsarCore WorldBorder §6═══════════════"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §ePosition & Size:"))
        source.sendMessage(Text.literal("§7  /pc worldborder center <x> <z> §8- Set border center"))
        source.sendMessage(Text.literal("§7  /pc worldborder set <distance> §8- Set border size"))
        source.sendMessage(Text.literal("§7  /pc worldborder add <distance> [time] §8- Change border size"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eDamage & Safety:"))
        source.sendMessage(Text.literal("§7  /pc worldborder damage amount <damage> §8- Set damage per block"))
        source.sendMessage(Text.literal("§7  /pc worldborder damage buffer <distance> §8- Set safe zone"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWarnings:"))
        source.sendMessage(Text.literal("§7  /pc worldborder warning distance <distance> §8- Warning distance"))
        source.sendMessage(Text.literal("§7  /pc worldborder warning time <time> §8- Warning time"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eInformation:"))
        source.sendMessage(Text.literal("§7  /pc worldborder get §8- Show current border settings"))
        source.sendMessage(Text.literal("§7  /pc worldborder refresh §8- Force refresh border visual"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§c▌ §7Note: All settings are per-world and automatically saved"))
        source.sendMessage(Text.literal("§6═══════════════════════════════════════════════════"))

        return 1
    }

    private fun executeRefresh(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val source = context.source
            val world = source.world
            if (world !is ServerWorld) return 0

            // Force refresh worldborder visual for all players in this world
            com.g2806.pulsarcore.world.WorldBorderManager.sendWorldBorderUpdateToPlayers(world)
            
            // If command was run by a player, also force refresh for them specifically
            if (source.player != null) {
                com.g2806.pulsarcore.world.WorldBorderManager.forceWorldBorderUpdateForPlayer(source.player!!)
            }

            val border = world.worldBorder
            source.sendMessage(Text.literal("§aForce-refreshed WorldBorder visual for dimension §6${world.registryKey.value}"))
            source.sendMessage(Text.literal("§7Current border: Size=§6${String.format("%.1f", border.size)}§7, Center=§6${String.format("%.1f", border.centerX)}§7,§6${String.format("%.1f", border.centerZ)}"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Error refreshing world border", e)
            context.source.sendError(Text.literal("§cFailed to refresh world border"))
        }
        return 1
    }
}