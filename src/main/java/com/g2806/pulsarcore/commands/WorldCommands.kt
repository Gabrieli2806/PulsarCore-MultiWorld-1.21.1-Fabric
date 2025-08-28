package com.g2806.pulsarcore.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.data.PulsarWorldData
import com.g2806.pulsarcore.suggestion.DifficultySuggestionProvider
import com.g2806.pulsarcore.suggestion.WorldsDimensionSuggestionProvider
import com.g2806.pulsarcore.world.WorldManager
import net.minecraft.block.*
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.block.entity.SignText
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.Difficulty
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import kotlin.random.Random

object WorldCommands {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        registerCreateWorldCommand(dispatcher)
        registerDeleteWorldCommand(dispatcher)
        registerTeleportToWorldCommand(dispatcher)
        registerSetWorldSpawnCommand(dispatcher)
        registerSetDestinationCommand(dispatcher)
    }

    private fun registerCreateWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("pc")
            .then(CommandManager.literal("createworld")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("worldName", StringArgumentType.string())
                        .executes(::executeCreateWorld)
                        .then(
                            CommandManager.argument("worldDifficulty", StringArgumentType.string())
                                .suggests(DifficultySuggestionProvider())
                                .executes(::executeCreateWorld)
                                .then(
                                    CommandManager.argument("worldDimension", DimensionArgumentType.dimension())
                                        .suggests(WorldsDimensionSuggestionProvider())
                                        .executes(::executeCreateWorld)
                                        .then(
                                            CommandManager.argument("worldSeed", StringArgumentType.string())
                                                .executes(::executeCreateWorld)
                                        )
                                )
                        )
                )
            )
        )
    }

    private fun executeCreateWorld(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val server = context.source.server
            val worldName = StringArgumentType.getString(context, "worldName").lowercase()
            val worldIdentifier = Identifier.of(PulsarCore.MOD_ID, worldName)

            if (WorldManager.worldExists(worldIdentifier)) {
                context.source.sendError(Text.translatable("pulsarcore.text.cmd.world.exists", worldName))
                return 0
            }

            val worldDifficulty = getEnumArgument(context, "worldDifficulty", Difficulty::class.java, server.saveProperties.difficulty)
            val dimensionIdentifier = getIdentifierArgument(context, "worldDimension", net.minecraft.world.dimension.DimensionTypes.OVERWORLD_ID)
            val serverWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionIdentifier)) ?: server.getWorld(World.OVERWORLD)!!
            val worldSeed = getSeedArgument(context, "worldSeed", Random.nextLong())

            val worldConfig = RuntimeWorldConfig()
                .setDifficulty(worldDifficulty)
                .setDimensionType(serverWorld.dimensionEntry)
                .setGenerator(serverWorld.chunkManager.chunkGenerator)
                .setSeed(worldSeed)
                .setShouldTickTime(true)

            val pulsarWorldData = PulsarWorldData(worldIdentifier, dimensionIdentifier, worldConfig)
            val createdWorld = WorldManager.getOrCreateWorld(server, pulsarWorldData, true)

            // Set default GameRules and WorldBorder for new worlds
            WorldManager.setupDefaultWorldSettings(createdWorld, server)

            context.source.sendMessage(Text.translatable("pulsarcore.text.cmd.world.created", worldName))
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while creating the world.", e)
        }
        return 1
    }

    private fun registerDeleteWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("pc")
            .then(CommandManager.literal("deleteworld")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("worldName", DimensionArgumentType.dimension())
                        .suggests(WorldsDimensionSuggestionProvider())
                        .executes(::executeDeleteWorld)
                )
            )
        )
    }

    private fun executeDeleteWorld(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val worldName = IdentifierArgumentType.getIdentifier(context, "worldName")

            if (!WorldManager.worldExists(worldName)) {
                context.source.sendError(Text.translatable("pulsarcore.text.cmd.world.notexists.unspecified"))
                return 0
            }

            if (WorldManager.deleteWorld(worldName)) {
                context.source.sendMessage(Text.translatable("pulsarcore.text.cmd.world.deleted", worldName.toString()))
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while deleting the world.", e)
        }
        return 1
    }

    private fun registerTeleportToWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("pc")
            .then(CommandManager.literal("tp")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("worldIdentifier", DimensionArgumentType.dimension())
                        .suggests(WorldsDimensionSuggestionProvider())
                        .executes(::executeTeleportToWorld)
                )))
    }

    private fun executeTeleportToWorld(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val player = context.source.player ?: return 0
            val worldName = IdentifierArgumentType.getIdentifier(context, "worldIdentifier")
            val world = context.source.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldName))

            if (world == null) {
                context.source.sendError(Text.translatable("pulsarcore.text.cmd.world.notexists.unspecified"))
                return 0
            }

            WorldManager.teleportPlayerToWorld(player, world)
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while teleporting the player.", e)
        }
        return 1
    }

    private fun registerSetWorldSpawnCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("pc")
                .then(
                    CommandManager.literal("setSpawn")
                        .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                        .then(
                            CommandManager.argument("spawnRadius", IntegerArgumentType.integer(0)).executes(::executeSetWorldSpawn)
                        )
                        .executes(::executeSetWorldSpawn)
                ))
    }

    private fun executeSetWorldSpawn(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            val player = context.source.player

            if (player == null || world !is net.minecraft.server.world.ServerWorld) return 0

            val radius = getIntArgument(context, "spawnRadius", -1)

            WorldManager.setWorldSpawn(world, player, radius, context.source.server)

            context.source.sendMessage(Text.translatable("pulsarcore.text.cmd.world.spawnset", world.registryKey.value.toString()))
            context.source.sendMessage(Text.translatable("pulsarcore.text.cmd.world.spawnset.position",
                String.format("%.3f", player.x), String.format("%.3f", player.y), String.format("%.3f", player.z),
                String.format("%.3f", player.yaw), String.format("%.3f", player.pitch)))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while setting the world spawn.", e)
        }
        return 1
    }

    private fun registerSetDestinationCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("pc")
                .then(
                    CommandManager.literal("setdestination")
                        .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                        .then(
                            CommandManager.argument("worldIdentifier", DimensionArgumentType.dimension())
                                .suggests(WorldsDimensionSuggestionProvider())
                                .executes(::executeSetDestination)
                        )
                        .executes(::executeSetDestination)
                ))
    }

    private fun executeSetDestination(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val player = context.source.player
            if (player == null || player.world == null) return 0

            val world = player.world

            if (world is net.minecraft.server.world.ServerWorld) {
                val rayContext = RaycastContext(
                    player.eyePos,
                    player.eyePos.add(player.rotationVecClient.multiply(200.0)),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    player
                )

                val hitResult = world.raycast(rayContext)
                val blockState = world.getBlockState(hitResult.blockPos)

                if (blockState.block !is SignBlock && blockState.block !is WallSignBlock && 
                    blockState.block !is HangingSignBlock && blockState.block !is WallHangingSignBlock) {
                    context.source.sendError(Text.translatable("pulsarcore.text.cmd.sign.lookat"))
                    return 0
                }

                val worldIdentifier = try {
                    IdentifierArgumentType.getIdentifier(context, "worldIdentifier")
                } catch (e: IllegalArgumentException) {
                    context.source.sendError(Text.literal("§cYou must specify a world identifier"))
                    return 0
                }

                val serverWorld = context.source.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldIdentifier))

                if (serverWorld == null) {
                    context.source.sendError(Text.translatable("pulsarcore.text.cmd.world.notexists", worldIdentifier.toString()))
                    return 0
                }

                val signEntity = world.getBlockEntity(hitResult.blockPos) as SignBlockEntity? ?: return 0

                if (!signEntity.changeText({
                        SignText()
                            .withMessage(0, Text.literal("teleport"))
                            .withMessage(1, Text.literal(worldIdentifier.namespace))
                            .withMessage(2, Text.literal(worldIdentifier.path))
                    }, false)) {
                    context.source.sendError(Text.translatable("pulsarcore.text.cmd.sign.failed"))
                    return 0
                }

                context.source.sendMessage(Text.translatable("pulsarcore.text.cmd.sign.success", worldIdentifier.toString()))
            }

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while setting sign destination.", e)
        }
        return 1
    }

    // Utility functions
    private fun getStringArgument(context: CommandContext<ServerCommandSource>, argumentName: String, defaultValue: String): String {
        return try {
            StringArgumentType.getString(context, argumentName)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    private fun getIntArgument(context: CommandContext<ServerCommandSource>, argumentName: String, defaultValue: Int): Int {
        return try {
            IntegerArgumentType.getInteger(context, argumentName)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    private fun getIdentifierArgument(context: CommandContext<ServerCommandSource>, argumentName: String, defaultValue: Identifier): Identifier {
        return try {
            IdentifierArgumentType.getIdentifier(context, argumentName)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    private fun <T : Enum<T>> getEnumArgument(context: CommandContext<ServerCommandSource>, argumentName: String, enumClass: Class<T>, defaultValue: T): T {
        return try {
            enumClass.enumConstants.first { it.name == StringArgumentType.getString(context, argumentName) }
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    private fun getSeedArgument(context: CommandContext<ServerCommandSource>, argumentName: String, defaultValue: Long): Long {
        return try {
            val rawSeed = StringArgumentType.getString(context, argumentName)
            val parsedSeed = tryParseLong(rawSeed)

            if (parsedSeed != null) return parsedSeed

            var hashedSeed = 0L
            for (i in rawSeed.indices) {
                hashedSeed = 31 * hashedSeed + rawSeed[i].code
            }
            hashedSeed
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

    private fun tryParseLong(value: String): Long? {
        return try {
            value.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}