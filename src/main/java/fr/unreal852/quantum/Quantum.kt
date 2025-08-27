package fr.unreal852.quantum

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder
import net.minecraft.block.*
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.block.entity.SignText
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.*
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper.WrapperLookup
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.*
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.world.*
import net.minecraft.world.border.WorldBorder
import net.minecraft.world.border.WorldBorderListener
import net.minecraft.world.dimension.DimensionTypes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import xyz.nucleoid.fantasy.RuntimeWorldHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object Quantum : ModInitializer {

    const val MOD_ID = "quantum"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    private val WORLDS: MutableMap<Identifier, QuantumWorld> = ConcurrentHashMap()

    // =========================================================================
    // MAIN INITIALIZATION
    // =========================================================================

    override fun onInitialize() {
        registerCommands()

        ServerLifecycleEvents.SERVER_STARTED.register(ServerStarted { server: MinecraftServer ->
            loadWorlds(server)
            loadPortals(server)
        })

        UseBlockCallback.EVENT.register(::handlePlayerUseSign)
        ServerPlayerEvents.AFTER_RESPAWN.register(::handlePlayerRespawn)
    }

    // =========================================================================
    // WORLD MANAGEMENT
    // =========================================================================

    fun worldExists(identifier: Identifier): Boolean {
        return WORLDS.containsKey(identifier)
    }

    fun getOrCreateWorld(server: MinecraftServer, worldData: QuantumWorldData, saveToDisk: Boolean): QuantumWorld? {
        if (WORLDS.containsKey(worldData.worldId))
            return WORLDS[worldData.worldId]

        val fantasy = Fantasy.get(server)
        val runtimeWorldConfig = worldData.runtimeWorldConfig.setDimensionAndGenerator(server, worldData)
        val runtimeWorldHandle = fantasy.getOrOpenPersistentWorld(worldData.worldId, runtimeWorldConfig)

        val world = QuantumWorld(runtimeWorldHandle, worldData)
        WORLDS[worldData.worldId] = world

        // Setup WorldBorder listener for new worlds
        setupWorldBorderListener(world.serverWorld)

        if (saveToDisk)
            getQuantumState(server).addWorld(worldData)

        return world
    }

    fun deleteWorld(identifier: Identifier): Boolean {
        val world = WORLDS.getOrDefault(identifier, null) ?: return false
        val server = world.serverWorld.server
        val fantasy = Fantasy.get(world.serverWorld.server)

        if (!fantasy.tickDeleteWorld(world.serverWorld))
            return false

        val state = getQuantumState(server)
        state.removeWorld(world.worldData)
        WORLDS.remove(identifier)

        return true
    }

    private fun loadWorlds(server: MinecraftServer) {
        WORLDS.clear()
        val state = getQuantumState(server)

        for (worldData in state.getWorlds()) {
            val createdWorld = getOrCreateWorld(server, worldData, false)

            // Apply all saved GameRules to the world
            if (createdWorld != null) {
                LOGGER.info("Loading world '{}' with {} saved GameRules...", worldData.worldId, worldData.getAllGameRules().size)

                // Log which GameRules are being loaded for debugging
                worldData.getAllGameRules().forEach { (key, value) ->
                    LOGGER.debug("Loading GameRule: {} = {}", key, value)
                }

                applyGameRulesToWorld(createdWorld.serverWorld, worldData, server)
                LOGGER.info("Successfully loaded world '{}'.", worldData.worldId)
            }
        }
    }

    private fun applyGameRulesToWorld(world: ServerWorld, worldData: QuantumWorldData, server: MinecraftServer) {
        val gameRules = worldData.getAllGameRules()

        gameRules.forEach { (gameRuleName, value) ->
            try {
                applySpecificGameRule(world, gameRuleName, value, server)
            } catch (e: Exception) {
                LOGGER.warn("Failed to apply GameRule {} with value {} to world {}: {}",
                    gameRuleName, value, world.registryKey.value, e.message)
            }
        }
    }

    private fun applySpecificGameRule(world: ServerWorld, gameRuleName: String, value: String, server: MinecraftServer) {
        try {
            when (gameRuleName) {
                "keepinventory" -> world.gameRules.get(GameRules.KEEP_INVENTORY).set(value.toBoolean(), server)
                "dofiretick" -> world.gameRules.get(GameRules.DO_FIRE_TICK).set(value.toBoolean(), server)
                "domobloot" -> world.gameRules.get(GameRules.DO_MOB_LOOT).set(value.toBoolean(), server)
                "domobspawning" -> world.gameRules.get(GameRules.DO_MOB_SPAWNING).set(value.toBoolean(), server)
                "dotiledrops" -> world.gameRules.get(GameRules.DO_TILE_DROPS).set(value.toBoolean(), server)
                "doentitydrops" -> world.gameRules.get(GameRules.DO_ENTITY_DROPS).set(value.toBoolean(), server)
                "commandblockoutput" -> world.gameRules.get(GameRules.COMMAND_BLOCK_OUTPUT).set(value.toBoolean(), server)
                "naturalregeneration" -> world.gameRules.get(GameRules.NATURAL_REGENERATION).set(value.toBoolean(), server)
                "dodaylightcycle" -> world.gameRules.get(GameRules.DO_DAYLIGHT_CYCLE).set(value.toBoolean(), server)
                "logadmincommands" -> world.gameRules.get(GameRules.LOG_ADMIN_COMMANDS).set(value.toBoolean(), server)
                "showdeathmessages" -> world.gameRules.get(GameRules.SHOW_DEATH_MESSAGES).set(value.toBoolean(), server)
                "sendcommandfeedback" -> world.gameRules.get(GameRules.SEND_COMMAND_FEEDBACK).set(value.toBoolean(), server)
                "doweathercycle" -> world.gameRules.get(GameRules.DO_WEATHER_CYCLE).set(value.toBoolean(), server)
                "doimmediaterespawn" -> world.gameRules.get(GameRules.DO_IMMEDIATE_RESPAWN).set(value.toBoolean(), server)
                "drowningdamage" -> world.gameRules.get(GameRules.DROWNING_DAMAGE).set(value.toBoolean(), server)
                "falldamage" -> world.gameRules.get(GameRules.FALL_DAMAGE).set(value.toBoolean(), server)
                "firedamage" -> world.gameRules.get(GameRules.FIRE_DAMAGE).set(value.toBoolean(), server)
                "freezedamage" -> world.gameRules.get(GameRules.FREEZE_DAMAGE).set(value.toBoolean(), server)
                "dopatrolspawning" -> world.gameRules.get(GameRules.DO_PATROL_SPAWNING).set(value.toBoolean(), server)
                "dotraderSpawning" -> world.gameRules.get(GameRules.DO_TRADER_SPAWNING).set(value.toBoolean(), server)
                "dovinesSpread" -> world.gameRules.get(GameRules.DO_VINES_SPREAD).set(value.toBoolean(), server)
                "announceadvancements" -> world.gameRules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(value.toBoolean(), server)
                "disableelytramovementcheck" -> world.gameRules.get(GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK).set(value.toBoolean(), server)
                "doinsomniaPhantoms" -> world.gameRules.get(GameRules.DO_INSOMNIA).set(value.toBoolean(), server)
                "dowardenspawning" -> world.gameRules.get(GameRules.DO_WARDEN_SPAWNING).set(value.toBoolean(), server)
                "blocksexplosiondroploot" -> world.gameRules.get(GameRules.BLOCK_EXPLOSION_DROP_DECAY).set(value.toBoolean(), server)
                "mobexplosiondroploot" -> world.gameRules.get(GameRules.MOB_EXPLOSION_DROP_DECAY).set(value.toBoolean(), server)
                "tntexplosiondroploot" -> world.gameRules.get(GameRules.TNT_EXPLOSION_DROP_DECAY).set(value.toBoolean(), server)
                "globalSoundEvents" -> world.gameRules.get(GameRules.GLOBAL_SOUND_EVENTS).set(value.toBoolean(), server)
                "doLimitedCrafting" -> world.gameRules.get(GameRules.DO_LIMITED_CRAFTING).set(value.toBoolean(), server)
                // Mixed type GameRules - handle as boolean for now
                "playerssleepingpercentage" -> {
                    val rule = world.gameRules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)
                    if (rule is GameRules.IntRule) {
                        rule.set(if (value.toBoolean()) 100 else 0, server)
                    } else if (rule is GameRules.BooleanRule) {
                        rule.set(value.toBoolean(), server)
                    }
                }
                "snowaccumulationheight" -> {
                    val rule = world.gameRules.get(GameRules.SNOW_ACCUMULATION_HEIGHT)
                    if (rule is GameRules.IntRule) {
                        rule.set(if (value.toBoolean()) 1 else 0, server)
                    } else if (rule is GameRules.BooleanRule) {
                        rule.set(value.toBoolean(), server)
                    }
                }
                "waterSourceConversion" -> {
                    val rule = world.gameRules.get(GameRules.WATER_SOURCE_CONVERSION)
                    if (rule is GameRules.IntRule) {
                        rule.set(if (value.toBoolean()) 1 else 0, server)
                    } else if (rule is GameRules.BooleanRule) {
                        rule.set(value.toBoolean(), server)
                    }
                }
                "lavaSourceConversion" -> {
                    val rule = world.gameRules.get(GameRules.LAVA_SOURCE_CONVERSION)
                    if (rule is GameRules.IntRule) {
                        rule.set(if (value.toBoolean()) 1 else 0, server)
                    } else if (rule is GameRules.BooleanRule) {
                        rule.set(value.toBoolean(), server)
                    }
                }
                // Integer GameRules
                "randomtickspeed" -> world.gameRules.get(GameRules.RANDOM_TICK_SPEED).set(value.toInt(), server)
                "spawnradius" -> world.gameRules.get(GameRules.SPAWN_RADIUS).set(value.toInt(), server)
                "maxentitycramming" -> world.gameRules.get(GameRules.MAX_ENTITY_CRAMMING).set(value.toInt(), server)
                "maxcommandchainlength" -> world.gameRules.get(GameRules.MAX_COMMAND_CHAIN_LENGTH).set(value.toInt(), server)
                "commandmodificationblocklimit" -> world.gameRules.get(GameRules.COMMAND_MODIFICATION_BLOCK_LIMIT).set(value.toInt(), server)
                else -> LOGGER.warn("Unknown GameRule: {}", gameRuleName)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to apply GameRule {} with value {}: {}", gameRuleName, value, e.message)
        }
    }

    private fun applyWorldBorderToWorld(world: ServerWorld, worldData: QuantumWorldData, server: MinecraftServer) {
        val worldBorderProperties = worldData.getAllWorldBorderProperties()

        if (worldBorderProperties.isEmpty()) {
            LOGGER.debug("No WorldBorder properties found for world {}", world.registryKey.value)
            return
        }

        worldBorderProperties.forEach { (property, value) ->
            try {
                applySpecificWorldBorderProperty(world, property, value, worldData)
            } catch (e: Exception) {
                LOGGER.warn("Failed to apply WorldBorder property {} with value {} to world {}: {}",
                    property, value, world.registryKey.value, e.message)
            }
        }
    }

    private fun applySpecificWorldBorderProperty(world: ServerWorld, property: String, value: String, worldData: QuantumWorldData) {
        try {
            val border = world.worldBorder

            when (property) {
                "centerX" -> {
                    val centerZ = worldData.getWorldBorderProperty("centerZ")?.toDoubleOrNull() ?: border.centerZ
                    border.setCenter(value.toDouble(), centerZ)
                    LOGGER.debug("Applied WorldBorder center X: {} for world {}", value, world.registryKey.value)
                }
                "centerZ" -> {
                    val centerX = worldData.getWorldBorderProperty("centerX")?.toDoubleOrNull() ?: border.centerX
                    border.setCenter(centerX, value.toDouble())
                    LOGGER.debug("Applied WorldBorder center Z: {} for world {}", value, world.registryKey.value)
                }
                "size" -> {
                    border.setSize(value.toDouble())
                    LOGGER.debug("Applied WorldBorder size: {} for world {}", value, world.registryKey.value)
                }
                "damagePerBlock" -> {
                    border.damagePerBlock = value.toDouble()
                    LOGGER.debug("Applied WorldBorder damage: {} for world {}", value, world.registryKey.value)
                }
                "safeZone" -> {
                    border.safeZone = value.toDouble()
                    LOGGER.debug("Applied WorldBorder safe zone: {} for world {}", value, world.registryKey.value)
                }
                "warningTime" -> {
                    border.warningTime = value.toInt()
                    LOGGER.debug("Applied WorldBorder warning time: {} for world {}", value, world.registryKey.value)
                }
                "warningBlocks" -> {
                    border.warningBlocks = value.toInt()
                    LOGGER.debug("Applied WorldBorder warning blocks: {} for world {}", value, world.registryKey.value)
                }
                else -> LOGGER.warn("Unknown WorldBorder property: {}", property)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to apply WorldBorder property {} with value {}: {}", property, value, e.message)
        }
    }

    private fun loadPortals(server: MinecraftServer) {
        val state = getQuantumState(server)

        for (portal in state.getPortals()) {
            val itemPortal = Registries.ITEM.get(portal.portalIgniteItemId)
            LOGGER.info("Found portal '{}', loading it.", portal.destinationId)
            CustomPortalBuilder.beginPortal()
                .frameBlock(portal.portalBlockId)
                .lightWithItem(itemPortal)
                .destDimID(portal.destinationId)
                .tintColor(portal.portalColor)
                .registerPortal()
        }
    }

    // =========================================================================
    // EVENT HANDLERS
    // =========================================================================

    private fun handlePlayerRespawn(oldPlayer: ServerPlayerEntity, newPlayer: ServerPlayerEntity, alive: Boolean) {
        if (oldPlayer.spawnPointPosition != null) return

        val worldState = getWorldState(newPlayer.serverWorld)
        val playerPositionPacket = PlayerPositionLookS2CPacket(
            worldState.worldSpawnPos.x, worldState.worldSpawnPos.y, worldState.worldSpawnPos.z,
            worldState.worldSpawnAngle.x, worldState.worldSpawnAngle.y, PositionFlag.ROT, 0
        )
        newPlayer.networkHandler.sendPacket(playerPositionPacket)
    }

    private fun handlePlayerUseSign(player: PlayerEntity, world: World, hand: Hand, hitResult: BlockHitResult): ActionResult {
        if (world.isClient || player.isSpectator || player.isSneaking)
            return ActionResult.PASS

        val blockState = world.getBlockState(hitResult.blockPos)
        val block = blockState.block

        if (block !is SignBlock && block !is WallSignBlock && block !is HangingSignBlock && block !is WallHangingSignBlock)
            return ActionResult.PASS

        val signEntity = world.getBlockEntity(hitResult.blockPos)

        if (signEntity is SignBlockEntity) {
            val firstLine = signEntity.backText.getMessage(0, false).string
            if (!firstLine.equals("teleport", true))
                return ActionResult.PASS

            val secondLine = signEntity.backText.getMessage(1, false).string
            val thirdLine = signEntity.backText.getMessage(2, false).string
            if (secondLine.isNullOrEmpty() && thirdLine.isNullOrEmpty())
                return ActionResult.PASS

            val identifier = Identifier.of(secondLine, thirdLine)
            val targetWorld = world.server?.getWorld(RegistryKey.of(RegistryKeys.WORLD, identifier))

            if (targetWorld is ServerWorld) {
                player.teleportToWorld(targetWorld)
                return ActionResult.SUCCESS
            }
        }

        return ActionResult.PASS
    }

    // =========================================================================
    // COMMAND REGISTRATION
    // =========================================================================

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource>,
                                                                                 registryAccess: CommandRegistryAccess,
                                                                                 environment: RegistrationEnvironment ->
            if (!environment.integrated && !environment.dedicated) return@CommandRegistrationCallback

            registerCreateWorldCommand(dispatcher)
            registerDeleteWorldCommand(dispatcher)
            registerTeleportToWorldCommand(dispatcher)
            registerSetWorldSpawnCommand(dispatcher)
            registerSetTeleportSignCommand(dispatcher)
            registerCreatePortalCommand(dispatcher)
            registerDeletePortalCommand(dispatcher)
            registerGameRulesCommands(dispatcher)
            registerWorldBorderCommands(dispatcher)
            registerHelpCommands(dispatcher)
        })
    }

    // =========================================================================
    // COMMANDS IMPLEMENTATION
    // =========================================================================

    private fun registerCreateWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("qt")
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
            val worldIdentifier = Identifier.of(MOD_ID, worldName)

            if (worldExists(worldIdentifier)) {
                context.source.sendError(Text.translatable("quantum.text.cmd.world.exists", worldName))
                return 0
            }

            val worldDifficulty = getEnumArgument(context, "worldDifficulty", Difficulty::class.java, server.saveProperties.difficulty)
            val dimensionIdentifier = getIdentifierArgument(context, "worldDimension", DimensionTypes.OVERWORLD_ID)
            val serverWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionIdentifier)) ?: server.getWorld(World.OVERWORLD)!!
            val worldSeed = getSeedArgument(context, "worldSeed", Random.nextLong())

            val worldConfig = RuntimeWorldConfig()
                .setDifficulty(worldDifficulty)
                .setDimensionType(serverWorld.dimensionEntry)
                .setGenerator(serverWorld.chunkManager.chunkGenerator)
                .setSeed(worldSeed)
                .setShouldTickTime(true)

            val quantumWorldData = QuantumWorldData(worldIdentifier, dimensionIdentifier, worldConfig)
            val createdWorld = getOrCreateWorld(server, quantumWorldData, true)

            // Set default GameRules for new worlds
            createdWorld?.serverWorld?.let { world ->
                // Set some sensible defaults
                world.gameRules.get(GameRules.KEEP_INVENTORY).set(false, server)
                quantumWorldData.setGameRule("keepinventory", "false")

                // Set default WorldBorder (smaller than default for custom worlds)
                world.worldBorder.setCenter(0.0, 0.0)
                world.worldBorder.setSize(1000.0) // 1000 blocks by default instead of 60M
                quantumWorldData.setWorldBorderProperty("centerX", "0.0")
                quantumWorldData.setWorldBorderProperty("centerZ", "0.0")
                quantumWorldData.setWorldBorderProperty("size", "1000.0")

                // Setup WorldBorder listener for immediate updates
                setupWorldBorderListener(world)

                // Mark storage as dirty to save the new world data
                getQuantumState(server).markDirty()
            }

            context.source.sendMessage(Text.translatable("quantum.text.cmd.world.created", worldName))
        } catch (e: Exception) {
            LOGGER.error("An error occurred while creating the world.", e)
        }
        return 1
    }

    private fun registerWorldBorderCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // WorldBorder center command
        dispatcher.register(CommandManager.literal("qt")
            .then(CommandManager.literal("worldborder")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(CommandManager.literal("center")
                    .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                        .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                            .executes(::executeSetWorldBorderCenter)
                        )
                    )
                    .executes(::executeGetWorldBorderCenter)
                )
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("size", DoubleArgumentType.doubleArg(1.0))
                        .executes(::executeSetWorldBorderSize)
                    )
                    .executes(::executeGetWorldBorderSize)
                )
                .then(CommandManager.literal("damage")
                    .then(CommandManager.argument("damage", DoubleArgumentType.doubleArg(0.0))
                        .executes(::executeSetWorldBorderDamage)
                    )
                    .executes(::executeGetWorldBorderDamage)
                )
                .then(CommandManager.literal("buffer")
                    .then(CommandManager.argument("buffer", DoubleArgumentType.doubleArg(0.0))
                        .executes(::executeSetWorldBorderBuffer)
                    )
                    .executes(::executeGetWorldBorderBuffer)
                )
                .then(CommandManager.literal("warning")
                    .then(CommandManager.literal("time")
                        .then(CommandManager.argument("time", IntegerArgumentType.integer(0))
                            .executes(::executeSetWorldBorderWarningTime)
                        )
                        .executes(::executeGetWorldBorderWarningTime)
                    )
                    .then(CommandManager.literal("distance")
                        .then(CommandManager.argument("distance", IntegerArgumentType.integer(0))
                            .executes(::executeSetWorldBorderWarningDistance)
                        )
                        .executes(::executeGetWorldBorderWarningDistance)
                    )
                )
                .executes(::executeGetWorldBorderInfo)
            )
        )
    }

    private fun executeSetWorldBorderCenter(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val x = DoubleArgumentType.getDouble(context, "x")
            val z = DoubleArgumentType.getDouble(context, "z")

            world.worldBorder.setCenter(x, z)
            saveWorldBorderToWorldData(world, "centerX", x.toString())
            saveWorldBorderToWorldData(world, "centerZ", z.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder center set to §6$x, $z §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border center.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderCenter(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val border = world.worldBorder
            context.source.sendMessage(Text.literal("§6WorldBorder center: §e${border.centerX}, ${border.centerZ} §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border center.", e)
        }
        return 1
    }

    private fun executeSetWorldBorderSize(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val size = DoubleArgumentType.getDouble(context, "size")

            world.worldBorder.setSize(size)
            saveWorldBorderToWorldData(world, "size", size.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder size set to §6$size §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border size.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderSize(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val size = world.worldBorder.size
            context.source.sendMessage(Text.literal("§6WorldBorder size: §e$size §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border size.", e)
        }
        return 1
    }

    private fun executeSetWorldBorderDamage(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val damage = DoubleArgumentType.getDouble(context, "damage")

            world.worldBorder.damagePerBlock = damage
            saveWorldBorderToWorldData(world, "damagePerBlock", damage.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder damage set to §6$damage §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border damage.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderDamage(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val damage = world.worldBorder.damagePerBlock
            context.source.sendMessage(Text.literal("§6WorldBorder damage: §e$damage §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border damage.", e)
        }
        return 1
    }

    private fun executeSetWorldBorderBuffer(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val buffer = DoubleArgumentType.getDouble(context, "buffer")

            world.worldBorder.safeZone = buffer
            saveWorldBorderToWorldData(world, "safeZone", buffer.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder buffer set to §6$buffer §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border buffer.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderBuffer(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val buffer = world.worldBorder.safeZone
            context.source.sendMessage(Text.literal("§6WorldBorder buffer: §e$buffer §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border buffer.", e)
        }
        return 1
    }

    private fun executeSetWorldBorderWarningTime(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val time = IntegerArgumentType.getInteger(context, "time")

            world.worldBorder.warningTime = time
            saveWorldBorderToWorldData(world, "warningTime", time.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder warning time set to §6$time §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border warning time.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderWarningTime(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val time = world.worldBorder.warningTime
            context.source.sendMessage(Text.literal("§6WorldBorder warning time: §e$time §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border warning time.", e)
        }
        return 1
    }

    private fun executeSetWorldBorderWarningDistance(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = IntegerArgumentType.getInteger(context, "distance")

            world.worldBorder.warningBlocks = distance
            saveWorldBorderToWorldData(world, "warningBlocks", distance.toString())

            // Send update to all players in this world immediately
            sendWorldBorderUpdateToPlayers(world)

            context.source.sendMessage(Text.literal("§aWorldBorder warning distance set to §6$distance §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting world border warning distance.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderWarningDistance(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val distance = world.worldBorder.warningBlocks
            context.source.sendMessage(Text.literal("§6WorldBorder warning distance: §e$distance §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border warning distance.", e)
        }
        return 1
    }

    private fun executeGetWorldBorderInfo(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val border = world.worldBorder
            context.source.sendMessage(Text.literal(""))
            context.source.sendMessage(Text.literal("§6═══ WorldBorder Info for §e${world.registryKey.value} §6═══"))
            context.source.sendMessage(Text.literal("§6Center: §e${border.centerX}, ${border.centerZ}"))
            context.source.sendMessage(Text.literal("§6Size: §e${border.size}"))
            context.source.sendMessage(Text.literal("§6Damage: §e${border.damagePerBlock}"))
            context.source.sendMessage(Text.literal("§6Buffer: §e${border.safeZone}"))
            context.source.sendMessage(Text.literal("§6Warning Time: §e${border.warningTime}"))
            context.source.sendMessage(Text.literal("§6Warning Distance: §e${border.warningBlocks}"))
            context.source.sendMessage(Text.literal("§6═══════════════════════════════════"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting world border info.", e)
        }
        return 1
    }

    private fun saveWorldBorderToWorldData(world: ServerWorld, property: String, value: String) {
        try {
            val quantumStorage = getQuantumState(world.server)
            val worldData = quantumStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                worldData.setWorldBorderProperty(property, value)
                quantumStorage.markDirty()
                LOGGER.info("Saved WorldBorder '{}' = '{}' for world '{}'", property, value, world.registryKey.value)
            } else {
                LOGGER.warn("Could not find world data for '{}' to save WorldBorder property '{}'", world.registryKey.value, property)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to save WorldBorder property '{}' for world '{}': {}", property, world.registryKey.value, e.message)
        }
    }

    private fun setupWorldBorderListener(world: ServerWorld) {
        val border = world.worldBorder
        border.addListener(QuantumWorldBorderListener(world))
        LOGGER.debug("Setup WorldBorder listener for world {}", world.registryKey.value)
    }

    private fun sendWorldBorderUpdateToPlayers(world: ServerWorld) {
        val border = world.worldBorder
        val players = world.players

        // Send complete world border info to all players in this world
        players.forEach { player ->
            player.networkHandler.sendPacket(WorldBorderInitializeS2CPacket(border))
        }
        LOGGER.debug("Sent WorldBorder update to {} players in world {}", players.size, world.registryKey.value)
    }

    private fun saveWorldBorderState(world: ServerWorld) {
        try {
            val border = world.worldBorder
            val quantumStorage = getQuantumState(world.server)
            val worldData = quantumStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                // Save all current WorldBorder properties
                worldData.setWorldBorderProperty("centerX", border.centerX.toString())
                worldData.setWorldBorderProperty("centerZ", border.centerZ.toString())
                worldData.setWorldBorderProperty("size", border.size.toString())
                worldData.setWorldBorderProperty("damagePerBlock", border.damagePerBlock.toString())
                worldData.setWorldBorderProperty("safeZone", border.safeZone.toString())
                worldData.setWorldBorderProperty("warningTime", border.warningTime.toString())
                worldData.setWorldBorderProperty("warningBlocks", border.warningBlocks.toString())

                quantumStorage.markDirty()
                LOGGER.debug("Saved complete WorldBorder state for world {}", world.registryKey.value)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to save WorldBorder state for world {}: {}", world.registryKey.value, e.message)
        }
    }

    // =========================================================================
    // WORLD BORDER LISTENER (Enhanced from WorldBorderFix mod)
    // =========================================================================

    class QuantumWorldBorderListener(private val world: ServerWorld) : WorldBorderListener {
        override fun onSizeChange(border: WorldBorder, size: Double) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderSizeChangedS2CPacket(border))
            }
            // Auto-save when size changes
            saveWorldBorderProperty(world, "size", size.toString())
            LOGGER.debug("WorldBorder size changed to {} in world {}", size, world.registryKey.value)
        }

        override fun onInterpolateSize(border: WorldBorder, fromSize: Double, toSize: Double, time: Long) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderInitializeS2CPacket(border))
            }
            LOGGER.debug("WorldBorder size interpolating from {} to {} over {}ms in world {}", fromSize, toSize, time, world.registryKey.value)
        }

        override fun onCenterChanged(border: WorldBorder, centerX: Double, centerZ: Double) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderCenterChangedS2CPacket(border))
            }
            // Auto-save when center changes
            saveWorldBorderProperty(world, "centerX", centerX.toString())
            saveWorldBorderProperty(world, "centerZ", centerZ.toString())
            LOGGER.debug("WorldBorder center changed to {},{} in world {}", centerX, centerZ, world.registryKey.value)
        }

        override fun onWarningTimeChanged(border: WorldBorder, warningTime: Int) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderWarningTimeChangedS2CPacket(border))
            }
            // Auto-save when warning time changes
            saveWorldBorderProperty(world, "warningTime", warningTime.toString())
            LOGGER.debug("WorldBorder warning time changed to {} in world {}", warningTime, world.registryKey.value)
        }

        override fun onWarningBlocksChanged(border: WorldBorder, warningBlockDistance: Int) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderWarningBlocksChangedS2CPacket(border))
            }
            // Auto-save when warning blocks change
            saveWorldBorderProperty(world, "warningBlocks", warningBlockDistance.toString())
            LOGGER.debug("WorldBorder warning blocks changed to {} in world {}", warningBlockDistance, world.registryKey.value)
        }

        override fun onDamagePerBlockChanged(border: WorldBorder, damagePerBlock: Double) {
            // No packet needed for damage per block changes, but save it
            saveWorldBorderProperty(world, "damagePerBlock", damagePerBlock.toString())
            LOGGER.debug("WorldBorder damage per block changed to {} in world {}", damagePerBlock, world.registryKey.value)
        }

        override fun onSafeZoneChanged(border: WorldBorder, safeZoneRadius: Double) {
            // No packet needed for safe zone changes, but save it
            saveWorldBorderProperty(world, "safeZone", safeZoneRadius.toString())
            LOGGER.debug("WorldBorder safe zone changed to {} in world {}", safeZoneRadius, world.registryKey.value)
        }

        private fun saveWorldBorderProperty(world: ServerWorld, property: String, value: String) {
            try {
                val quantumStorage = getQuantumState(world.server)
                val worldData = quantumStorage.getWorlds().find { it.worldId == world.registryKey.value }

                if (worldData != null) {
                    worldData.setWorldBorderProperty(property, value)
                    quantumStorage.markDirty()
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to auto-save WorldBorder property {} for world {}: {}", property, world.registryKey.value, e.message)
            }
        }
    }

    private fun saveGameRuleToWorldData(world: ServerWorld, gameRuleName: String, value: String) {
        try {
            val quantumStorage = getQuantumState(world.server)
            val worldData = quantumStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                worldData.setGameRule(gameRuleName, value)
                quantumStorage.markDirty()
                LOGGER.info("Saved GameRule '{}' = '{}' for world '{}'", gameRuleName, value, world.registryKey.value)
            } else {
                LOGGER.warn("Could not find world data for '{}' to save GameRule '{}'", world.registryKey.value, gameRuleName)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to save GameRule '{}' for world '{}': {}", gameRuleName, world.registryKey.value, e.message)
        }
    }

    private fun registerGameRulesCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // Boolean GameRules
        val booleanGameRules = mapOf(
            "keepinventory" to GameRules.KEEP_INVENTORY,
            "dofiretick" to GameRules.DO_FIRE_TICK,
            "domobloot" to GameRules.DO_MOB_LOOT,
            "domobspawning" to GameRules.DO_MOB_SPAWNING,
            "dotiledrops" to GameRules.DO_TILE_DROPS,
            "doentitydrops" to GameRules.DO_ENTITY_DROPS,
            "commandblockoutput" to GameRules.COMMAND_BLOCK_OUTPUT,
            "naturalregeneration" to GameRules.NATURAL_REGENERATION,
            "dodaylightcycle" to GameRules.DO_DAYLIGHT_CYCLE,
            "logadmincommands" to GameRules.LOG_ADMIN_COMMANDS,
            "showdeathmessages" to GameRules.SHOW_DEATH_MESSAGES,
            "sendcommandfeedback" to GameRules.SEND_COMMAND_FEEDBACK,
            "doweathercycle" to GameRules.DO_WEATHER_CYCLE,
            "doimmediaterespawn" to GameRules.DO_IMMEDIATE_RESPAWN,
            "drowningdamage" to GameRules.DROWNING_DAMAGE,
            "falldamage" to GameRules.FALL_DAMAGE,
            "firedamage" to GameRules.FIRE_DAMAGE,
            "freezedamage" to GameRules.FREEZE_DAMAGE,
            "dopatrolspawning" to GameRules.DO_PATROL_SPAWNING,
            "dotraderSpawning" to GameRules.DO_TRADER_SPAWNING,
            "dovinesSpread" to GameRules.DO_VINES_SPREAD,
            "announceadvancements" to GameRules.ANNOUNCE_ADVANCEMENTS,
            "disableelytramovementcheck" to GameRules.DISABLE_ELYTRA_MOVEMENT_CHECK,
            "doinsomniaPhantoms" to GameRules.DO_INSOMNIA,
            "dowardenspawning" to GameRules.DO_WARDEN_SPAWNING,
            "blocksexplosiondroploot" to GameRules.BLOCK_EXPLOSION_DROP_DECAY,
            "mobexplosiondroploot" to GameRules.MOB_EXPLOSION_DROP_DECAY,
            "tntexplosiondroploot" to GameRules.TNT_EXPLOSION_DROP_DECAY,
            "globalSoundEvents" to GameRules.GLOBAL_SOUND_EVENTS,
            "doLimitedCrafting" to GameRules.DO_LIMITED_CRAFTING,
            "playerssleepingpercentage" to GameRules.PLAYERS_SLEEPING_PERCENTAGE,
            "snowaccumulationheight" to GameRules.SNOW_ACCUMULATION_HEIGHT,
            "waterSourceConversion" to GameRules.WATER_SOURCE_CONVERSION,
            "lavaSourceConversion" to GameRules.LAVA_SOURCE_CONVERSION
        )

        val integerGameRules = mapOf(
            "randomtickspeed" to GameRules.RANDOM_TICK_SPEED,
            "spawnradius" to GameRules.SPAWN_RADIUS,
            "maxentitycramming" to GameRules.MAX_ENTITY_CRAMMING,
            "maxcommandchainlength" to GameRules.MAX_COMMAND_CHAIN_LENGTH,
            "commandmodificationblocklimit" to GameRules.COMMAND_MODIFICATION_BLOCK_LIMIT
        )

        // Register boolean GameRules
        booleanGameRules.forEach { (name, gameRule) ->
            dispatcher.register(CommandManager.literal("qtg")
                .then(CommandManager.literal(name)
                    .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                    .then(
                        CommandManager.argument("value", BoolArgumentType.bool())
                            .executes { context -> executeSetBooleanGameRule(context, gameRule, name) }
                    )
                    .executes { context -> executeGetBooleanGameRule(context, gameRule, name) }
                )
            )
        }

        // Register integer GameRules
        integerGameRules.forEach { (name, gameRule) ->
            dispatcher.register(CommandManager.literal("qtg")
                .then(CommandManager.literal(name)
                    .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                    .then(
                        CommandManager.argument("value", IntegerArgumentType.integer())
                            .executes { context -> executeSetIntegerGameRule(context, gameRule, name) }
                    )
                    .executes { context -> executeGetIntegerGameRule(context, gameRule, name) }
                )
            )
        }
    }

    private fun registerHelpCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // Main /qt command - shows basic commands
        dispatcher.register(CommandManager.literal("qt")
            .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
            .executes(::executeQtHelp)
        )

        // GameRules /qtg command - shows all gamerule commands
        dispatcher.register(CommandManager.literal("qtg")
            .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
            .executes(::executeQtgHelp)
        )
    }

    private fun executeQtHelp(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        val source = context.source
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6═══════════════ §eQuantum Commands §6═══════════════"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Management:"))
        source.sendMessage(Text.literal("§7  /qt createworld <name> [difficulty] [dimension] [seed] §8- Create new world"))
        source.sendMessage(Text.literal("§7  /qt deleteworld <world> §8- Delete existing world"))
        source.sendMessage(Text.literal("§7  /qt tp <world> §8- Teleport to world"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Configuration:"))
        source.sendMessage(Text.literal("§7  /qt setSpawn [radius] §8- Set world spawn point"))
        source.sendMessage(Text.literal("§7  /qt setdestination <world> §8- Set teleport sign destination"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §ePortal System:"))
        source.sendMessage(Text.literal("§7  /qt createportal <block> <item> <destination> §8- Create portal"))
        source.sendMessage(Text.literal("§7  /qt deleteportal <block> §8- Delete portal"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorldBorder:"))
        source.sendMessage(Text.literal("§7  /qt worldborder §8- WorldBorder configuration"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eGameRules:"))
        source.sendMessage(Text.literal("§7  /qtg §8- Show all GameRule commands"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6═══════════════════════════════════════════════════"))

        return 1
    }

    private fun executeQtgHelp(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        val source = context.source
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6═══════════════ §eQuantum GameRules §6═══════════════"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Mechanics §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg dofiretick <true|false> §8- Fire spread"))
        source.sendMessage(Text.literal("§7  /qtg domobloot <true|false> §8- Mob drops"))
        source.sendMessage(Text.literal("§7  /qtg domobspawning <true|false> §8- Mob spawning"))
        source.sendMessage(Text.literal("§7  /qtg dotiledrops <true|false> §8- Block drops"))
        source.sendMessage(Text.literal("§7  /qtg doentitydrops <true|false> §8- Entity drops"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Cycles §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg dodaylightcycle <true|false> §8- Day/night cycle"))
        source.sendMessage(Text.literal("§7  /qtg doweathercycle <true|false> §8- Weather cycle"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eDamage Types §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg drowningdamage <true|false> §8- Drowning damage"))
        source.sendMessage(Text.literal("§7  /qtg falldamage <true|false> §8- Fall damage"))
        source.sendMessage(Text.literal("§7  /qtg firedamage <true|false> §8- Fire damage"))
        source.sendMessage(Text.literal("§7  /qtg freezedamage <true|false> §8- Freeze damage"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eSpecial Spawns §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg dopatrolspawning <true|false> §8- Pillager patrols"))
        source.sendMessage(Text.literal("§7  /qtg dotraderSpawning <true|false> §8- Wandering traders"))
        source.sendMessage(Text.literal("§7  /qtg dowardenspawning <true|false> §8- Warden spawning"))
        source.sendMessage(Text.literal("§7  /qtg doinsomniaPhantoms <true|false> §8- Insomnia phantoms"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §ePlayer Mechanics §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg keepinventory <true|false> §8- Keep inventory on death"))
        source.sendMessage(Text.literal("§7  /qtg naturalregeneration <true|false> §8- Natural regeneration"))
        source.sendMessage(Text.literal("§7  /qtg doimmediaterespawn <true|false> §8- Immediate respawn"))
        source.sendMessage(Text.literal("§7  /qtg announceadvancements <true|false> §8- Announce advancements"))
        source.sendMessage(Text.literal("§7  /qtg doLimitedCrafting <true|false> §8- Limited crafting"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eSystem §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /qtg commandblockoutput <true|false> §8- Command block output"))
        source.sendMessage(Text.literal("§7  /qtg logadmincommands <true|false> §8- Log admin commands"))
        source.sendMessage(Text.literal("§7  /qtg showdeathmessages <true|false> §8- Death messages"))
        source.sendMessage(Text.literal("§7  /qtg sendcommandfeedback <true|false> §8- Command feedback"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eNumerical Settings §7(Integer):"))
        source.sendMessage(Text.literal("§7  /qtg randomtickspeed <number> §8- Random tick speed"))
        source.sendMessage(Text.literal("§7  /qtg spawnradius <number> §8- Spawn radius"))
        source.sendMessage(Text.literal("§7  /qtg maxentitycramming <number> §8- Max entity cramming"))
        source.sendMessage(Text.literal("§7  /qtg maxcommandchainlength <number> §8- Max command chain length"))
        source.sendMessage(Text.literal("§7  /qtg commandmodificationblocklimit <number> §8- Command block limit"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eOther Settings §7(Mixed):"))
        source.sendMessage(Text.literal("§7  /qtg playerssleepingpercentage <true|false> §8- Sleep percentage"))
        source.sendMessage(Text.literal("§7  /qtg snowaccumulationheight <true|false> §8- Snow accumulation"))
        source.sendMessage(Text.literal("§7  /qtg waterSourceConversion <true|false> §8- Water source conversion"))
        source.sendMessage(Text.literal("§7  /qtg lavaSourceConversion <true|false> §8- Lava source conversion"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§c▌ §7Usage: Command without value shows current setting"))
        source.sendMessage(Text.literal("§6═══════════════════════════════════════════════════"))

        return 1
    }

    private fun executeSetBooleanGameRule(context: CommandContext<ServerCommandSource>, gameRule: GameRules.Key<out GameRules.Rule<*>>, ruleName: String): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val value = BoolArgumentType.getBool(context, "value")

            // Handle different rule types
            val rule = world.gameRules.get(gameRule)
            when (rule) {
                is GameRules.BooleanRule -> {
                    rule.set(value, context.source.server)
                    // Save to world data
                    saveGameRuleToWorldData(world, ruleName, value.toString())
                }
                is GameRules.IntRule -> {
                    val intValue = if (value) 100 else 0 // Convert boolean to int for special cases
                    rule.set(intValue, context.source.server)
                    saveGameRuleToWorldData(world, ruleName, value.toString()) // Save original boolean value
                }
                else -> {
                    context.source.sendError(Text.literal("§cUnsupported rule type for $ruleName"))
                    return 0
                }
            }

            val status = if (value) "enabled" else "disabled"
            context.source.sendMessage(Text.literal("§aGameRule §6$ruleName §aset to §6$status §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting game rule $ruleName.", e)
        }
        return 1
    }

    private fun executeGetBooleanGameRule(context: CommandContext<ServerCommandSource>, gameRule: GameRules.Key<out GameRules.Rule<*>>, ruleName: String): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val rule = world.gameRules.get(gameRule)
            val status = when (rule) {
                is GameRules.BooleanRule -> if (rule.get()) "enabled" else "disabled"
                is GameRules.IntRule -> if (rule.get() > 0) "enabled (${rule.get()})" else "disabled"
                else -> "unknown"
            }

            context.source.sendMessage(Text.literal("§6$ruleName §ais §6$status §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting game rule $ruleName.", e)
        }
        return 1
    }

    private fun executeSetIntegerGameRule(context: CommandContext<ServerCommandSource>, gameRule: GameRules.Key<out GameRules.Rule<*>>, ruleName: String): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val value = IntegerArgumentType.getInteger(context, "value")

            val rule = world.gameRules.get(gameRule)
            when (rule) {
                is GameRules.IntRule -> {
                    rule.set(value, context.source.server)
                    // Save to world data
                    saveGameRuleToWorldData(world, ruleName, value.toString())
                }
                else -> {
                    context.source.sendError(Text.literal("§cRule $ruleName is not an integer rule"))
                    return 0
                }
            }

            context.source.sendMessage(Text.literal("§aGameRule §6$ruleName §aset to §6$value §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting game rule $ruleName.", e)
        }
        return 1
    }

    private fun executeGetIntegerGameRule(context: CommandContext<ServerCommandSource>, gameRule: GameRules.Key<out GameRules.Rule<*>>, ruleName: String): Int {
        if (context.source == null) return 0

        try {
            val world = context.source.world
            if (world !is ServerWorld) return 0

            val rule = world.gameRules.get(gameRule)
            val value = when (rule) {
                is GameRules.IntRule -> rule.get()
                else -> {
                    context.source.sendError(Text.literal("§cRule $ruleName is not an integer rule"))
                    return 0
                }
            }

            context.source.sendMessage(Text.literal("§6$ruleName §ais §6$value §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while getting game rule $ruleName.", e)
        }
        return 1
    }

    private fun registerDeleteWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("qt")
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

            if (!worldExists(worldName)) {
                context.source.sendError(Text.translatable("quantum.text.cmd.world.notexists.unspecified"))
                return 0
            }

            if (deleteWorld(worldName)) {
                context.source.sendMessage(Text.translatable("quantum.text.cmd.world.deleted", worldName.toString()))
            }
        } catch (e: Exception) {
            LOGGER.error("An error occurred while deleting the world.", e)
        }
        return 1
    }

    private fun registerTeleportToWorldCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("qt")
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
                context.source.sendError(Text.translatable("quantum.text.cmd.world.notexists.unspecified"))
                return 0
            }

            player.teleportToWorld(world)
        } catch (e: Exception) {
            LOGGER.error("An error occurred while teleporting the player.", e)
        }
        return 1
    }

    private fun registerSetWorldSpawnCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("qt")
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

            if (player == null || world !is ServerWorld) return 0

            val radius = getIntArgument(context, "spawnRadius", -1)

            if (radius >= 0)
                world.gameRules.get(GameRules.SPAWN_RADIUS).set(radius, context.source.server)

            world.setCustomSpawnPos(player.pos, player.yaw, player.pitch)

            context.source.sendMessage(Text.translatable("quantum.text.cmd.world.spawnset", world.registryKey.value.toString()))
            context.source.sendMessage(Text.translatable("quantum.text.cmd.world.spawnset.position",
                String.format("%.3f", player.x), String.format("%.3f", player.y), String.format("%.3f", player.z),
                String.format("%.3f", player.yaw), String.format("%.3f", player.pitch)))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while setting the world spawn.", e)
        }
        return 1
    }

    private fun registerSetTeleportSignCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("qt")
                .then(
                    CommandManager.literal("setdestination")
                        .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                        .then(
                            CommandManager.argument("worldIdentifier", DimensionArgumentType.dimension())
                                .suggests(WorldsDimensionSuggestionProvider())
                                .executes(::executeSetTeleportSign)
                        )
                        .executes(::executeSetTeleportSign)
                ))
    }

    private fun executeSetTeleportSign(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val player = context.source.player
            if (player == null || player.world == null) return 0

            val world = player.world

            if (world is ServerWorld) {
                val rayContext = RaycastContext(
                    player.eyePos,
                    player.eyePos.add(player.rotationVecClient.multiply(200.0)),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    player
                )

                val hitResult = world.raycast(rayContext)
                val blockState = world.getBlockState(hitResult.blockPos)

                if (blockState.block !is SignBlock && blockState.block !is WallSignBlock && blockState.block !is HangingSignBlock && blockState.block !is WallHangingSignBlock) {
                    context.source.sendError(Text.translatable("quantum.text.cmd.sign.lookat"))
                    return 0
                }

                val worldIdentifier = IdentifierArgumentType.getIdentifier(context, "worldIdentifier")
                val serverWorld = context.source.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, worldIdentifier))

                if (serverWorld == null) {
                    context.source.sendError(Text.translatable("quantum.text.cmd.world.notexists", worldIdentifier.toString()))
                    return 0
                }

                val signEntity = world.getBlockEntity(hitResult.blockPos) as SignBlockEntity? ?: return 0

                if (!signEntity.changeText({
                        SignText()
                            .withMessage(0, Text.literal("teleport"))
                            .withMessage(1, Text.literal(worldIdentifier.namespace))
                            .withMessage(2, Text.literal(worldIdentifier.path))
                    }, false)) {
                    context.source.sendError(Text.translatable("quantum.text.cmd.sign.failed"))
                    return 0
                }

                context.source.sendMessage(Text.translatable("quantum.text.cmd.sign.success", worldIdentifier.toString()))
            }

        } catch (e: Exception) {
            LOGGER.error("An error occurred while teleporting the player.", e)
        }
        return 1
    }

    private fun registerCreatePortalCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("qt")
            .then(CommandManager.literal("createportal")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("portalFrameBlock", IdentifierArgumentType.identifier())
                        .suggests(BlocksSuggestionProvider())
                        .then(
                            CommandManager.argument("portalItem", IdentifierArgumentType.identifier())
                                .suggests(ItemsSuggestionProvider())
                                .then(
                                    CommandManager.argument("destinationWorld", DimensionArgumentType.dimension())
                                        .suggests(WorldsDimensionSuggestionProvider())
                                        .executes(::executeCreatePortal)
                                )
                        )
                )
            )
        )
    }

    private fun executeCreatePortal(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val quantumStorage = getQuantumState(context.source.server)
            val portalBlockId = IdentifierArgumentType.getIdentifier(context, "portalFrameBlock")

            if (quantumStorage.getPortal { it.portalBlockId == portalBlockId } != null) {
                context.source.sendError(Text.translatable("quantum.text.cmd.portal.exists", portalBlockId.toString()))
                return 0
            }

            val destinationId = IdentifierArgumentType.getIdentifier(context, "destinationWorld")
            val portalItemId = IdentifierArgumentType.getIdentifier(context, "portalItem")

            val portalBlock = Registries.BLOCK.get(portalBlockId)
            val portalItem = Registries.ITEM.get(portalItemId)

            val portalLink = CustomPortalBuilder.beginPortal()
                .frameBlock(portalBlock)
                .lightWithItem(portalItem)
                .destDimID(destinationId)
                .tintColor(240, 142, 25)
                .registerPortal()

            context.source.sendMessage(Text.translatable("quantum.text.cmd.portal.created"))

            quantumStorage.addPortal(QuantumPortalData(portalLink.dimID, portalLink.block, portalItemId, portalLink.colorID))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while creating the world.", e)
        }
        return 1
    }

    private fun registerDeletePortalCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("qt")
            .then(CommandManager.literal("deleteportal")
                .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("portalFrameBlock", IdentifierArgumentType.identifier())
                        .suggests(BlocksSuggestionProvider())
                        .executes(::executeDeletePortal)
                )
            )
        )
    }

    private fun executeDeletePortal(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        try {
            val portalBlockId = IdentifierArgumentType.getIdentifier(context, "portalFrameBlock")
            val quantumStorage = getQuantumState(context.source.server)
            val portal = quantumStorage.getPortal { it.portalBlockId == portalBlockId }

            if (portal == null) {
                context.source.sendError(Text.translatable("quantum.text.cmd.portal.notexists", portalBlockId.toString()))
                return 0
            }

            if (quantumStorage.removePortal(portal))
                context.source.sendMessage(Text.translatable("quantum.text.cmd.world.deleted"))

        } catch (e: Exception) {
            LOGGER.error("An error occurred while deleting the portal.", e)
        }
        return 1
    }

    // =========================================================================
    // SUGGESTION PROVIDERS
    // =========================================================================

    class BlocksSuggestionProvider : com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
            for (block in Registries.BLOCK) {
                val blockState = block.defaultState

                if (block is BlockWithEntity || block is PlantBlock) continue

                if (blockState.isIn(
                        BlockTags.AIR, BlockTags.DOORS, BlockTags.BUTTONS, BlockTags.ALL_SIGNS, BlockTags.SLABS, BlockTags.STAIRS,
                        BlockTags.BEDS, BlockTags.BANNERS, BlockTags.RAILS, BlockTags.TRAPDOORS,
                        BlockTags.PRESSURE_PLATES, BlockTags.FENCES, BlockTags.FENCE_GATES, BlockTags.SAPLINGS, BlockTags.WOOL_CARPETS
                    )) continue

                builder.suggest(blockState.registryEntry.idAsString)
            }
            return builder.buildFuture()
        }
    }

    class DifficultySuggestionProvider : com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
            for (difficulty in Difficulty.entries) {
                builder.suggest(difficulty.name)
            }
            return builder.buildFuture()
        }
    }

    class ItemsSuggestionProvider : com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
            for (item in Registries.ITEM) {
                if (item != Items.AIR && item !is BlockItem) {
                    builder.suggest(item.toString())
                }
            }
            return builder.buildFuture()
        }
    }

    class WorldsDimensionSuggestionProvider : com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> {
        override fun getSuggestions(context: CommandContext<ServerCommandSource>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder): CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> {
            for (world in context.source.worldKeys) {
                builder.suggest(world.value.toString())
            }
            return builder.buildFuture()
        }
    }

    // =========================================================================
    // UTILITY FUNCTIONS
    // =========================================================================

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

    private fun getBoolArgument(context: CommandContext<ServerCommandSource>, argumentName: String, defaultValue: Boolean): Boolean {
        return try {
            BoolArgumentType.getBool(context, argumentName)
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

    // =========================================================================
    // EXTENSION FUNCTIONS
    // =========================================================================

    private fun NbtCompound.getIdentifier(key: String): Identifier {
        return Identifier.of(this.getString(key))
    }

    private fun PlayerEntity.teleportToWorld(targetWorld: ServerWorld) {
        val worldState = getWorldState(targetWorld)
        val teleportTarget = TeleportTarget(
            targetWorld, worldState.worldSpawnPos,
            Vec3d.ZERO, worldState.worldSpawnAngle.x, worldState.worldSpawnAngle.y, false
        ) {}
        this.teleportTo(teleportTarget)
    }

    private fun ServerWorld.setCustomSpawnPos(pos: Vec3d, yaw: Float, pitch: Float) {
        val worldState = getWorldState(this)
        worldState.setWorldSpawn(pos, yaw, pitch)
    }

    private fun RuntimeWorldConfig.setDimensionAndGenerator(server: MinecraftServer, worldData: QuantumWorldData): RuntimeWorldConfig {
        val worldRegKey = RegistryKey.of(RegistryKeys.WORLD, worldData.dimensionId)
        var dimensionWorld = server.getWorld(worldRegKey)

        if (dimensionWorld == null) {
            LOGGER.error("Failed to retrieve dimension ${worldData.dimensionId}. Defaulting to minecraft:overworld")
            dimensionWorld = server.overworld
        }

        worldData.runtimeWorldConfig.setDimensionType(dimensionWorld!!.dimensionEntry)
            .setGenerator(dimensionWorld.chunkManager.chunkGenerator)
        return this
    }

    private fun BlockState.isIn(vararg tags: TagKey<Block>): Boolean {
        for (tag in tags) {
            if (this.isIn(tag)) return true
        }
        return false
    }

    // =========================================================================
    // STORAGE CLASSES
    // =========================================================================

    private fun getQuantumState(server: MinecraftServer): QuantumStorage {
        val stateManager = server.overworld.persistentStateManager
        val quantumState = stateManager.getOrCreate(QuantumStorage.PersistentStateTypeLoader, MOD_ID)
        quantumState.markDirty()
        return quantumState
    }

    private fun getWorldState(world: ServerWorld): QuantumWorldStorage {
        val worldState = world.persistentStateManager.getOrCreate(QuantumWorldStorage.PersistentStateTypeLoader, "${MOD_ID}_world")
        if (worldState.worldSpawnPos == Vec3d.ZERO)
            worldState.setWorldSpawn(world.spawnPos.toBottomCenterPos(), 0f, 0f)
        worldState.markDirty()
        return worldState
    }

    class QuantumStorage : PersistentState() {
        private val worlds: MutableList<QuantumWorldData> = ArrayList()
        private val portals: MutableList<QuantumPortalData> = ArrayList()

        fun getWorlds(): List<QuantumWorldData> = worlds
        fun getPortals(): List<QuantumPortalData> = portals
        fun getPortal(predicate: (QuantumPortalData) -> Boolean): QuantumPortalData? = portals.find(predicate)

        fun addWorld(worldData: QuantumWorldData) {
            worlds.add(worldData)
            markDirty()
        }

        fun addPortal(portalData: QuantumPortalData) {
            portals.add(portalData)
            markDirty()
        }

        fun removeWorld(worldData: QuantumWorldData) {
            worlds.remove(worldData)
            markDirty()
        }

        fun removeWorld(predicate: (QuantumWorldData) -> Boolean): Boolean {
            val world = worlds.find(predicate) ?: return false
            worlds.remove(world)
            markDirty()
            return true
        }

        fun removePortal(portalData: QuantumPortalData): Boolean {
            return portals.remove(portalData)
        }

        fun removePortal(predicate: (QuantumPortalData) -> Boolean): Boolean {
            val portal = portals.find(predicate) ?: return false
            portals.remove(portal)
            markDirty()
            return true
        }

        override fun writeNbt(nbt: NbtCompound, registryLookup: WrapperLookup): NbtCompound {
            val worldsNbtList = NbtList()
            val portalsNbtList = NbtList()

            for (entry in worlds) {
                val entryNbt = NbtCompound()
                entry.writeToNbt(entryNbt)
                worldsNbtList.add(entryNbt)
            }

            for (entry in portals) {
                val entryNbt = NbtCompound()
                entry.writeToNbt(entryNbt)
                portalsNbtList.add(entryNbt)
            }
            nbt.put("worlds", worldsNbtList)
            nbt.put("portals", portalsNbtList)
            return nbt
        }

        companion object {
            val PersistentStateTypeLoader = PersistentState.Type(
                { QuantumStorage() },
                { nbt: NbtCompound, registryLookup: WrapperLookup -> fromNbt(nbt, registryLookup) },
                null
            )

            @Suppress("UNUSED_PARAMETER")
            private fun fromNbt(nbt: NbtCompound, registryLookup: WrapperLookup): QuantumStorage {
                val quantumStorage = QuantumStorage()
                val worldsNbtList = nbt.getList("worlds", 10) // 10 is the NbtCompound type
                val portalsNbt = nbt.getList("portals", 10)
                for (i in worldsNbtList.indices) {
                    val entryNbt = worldsNbtList.getCompound(i)
                    quantumStorage.worlds.add(QuantumWorldData.fromNbt(entryNbt))
                }
                for (i in portalsNbt.indices) {
                    val entryNbt = portalsNbt.getCompound(i)
                    quantumStorage.portals.add(QuantumPortalData.fromNbt(entryNbt))
                }
                return quantumStorage
            }
        }
    }

    class QuantumWorldStorage : PersistentState() {
        var worldSpawnPos: Vec3d = Vec3d.ZERO
            private set
        var worldSpawnAngle = Vec2f(0.0f, 0.0f) // X is yaw, Y is pitch
            private set

        fun setWorldSpawn(worldSpawn: Vec3d, worldSpawnYaw: Float, worldSpawnPitch: Float) {
            this.worldSpawnPos = worldSpawn
            this.worldSpawnAngle = Vec2f(worldSpawnYaw, worldSpawnPitch)
            markDirty()
        }

        override fun writeNbt(nbt: NbtCompound, registryLookup: WrapperLookup): NbtCompound {
            // write spawn pos
            nbt.putDouble("spawnposx", worldSpawnPos.x)
            nbt.putDouble("spawnposy", worldSpawnPos.y)
            nbt.putDouble("spawnposz", worldSpawnPos.z)

            // write spawn angle
            nbt.putFloat("spawnposyaw", worldSpawnAngle.x)
            nbt.putFloat("spawnpospitch", worldSpawnAngle.y)
            return nbt
        }

        companion object {
            val PersistentStateTypeLoader = PersistentState.Type(
                { QuantumWorldStorage() },
                { nbt: NbtCompound, registryLookup: WrapperLookup -> fromNbt(nbt, registryLookup) },
                null
            )

            @Suppress("UNUSED_PARAMETER")
            fun fromNbt(nbt: NbtCompound, registryLookup: WrapperLookup): QuantumWorldStorage {
                val worldState = QuantumWorldStorage()

                // get spawn pos
                val spawnPosX = nbt.getDouble("spawnposx")
                val spawnPosY = nbt.getDouble("spawnposy")
                val spawnPosZ = nbt.getDouble("spawnposz")

                // get spawn angle
                val spawnPosYaw = nbt.getFloat("spawnposyaw")
                val spawnPosPitch = nbt.getFloat("spawnpospitch")

                worldState.worldSpawnPos = Vec3d(spawnPosX, spawnPosY, spawnPosZ)
                worldState.worldSpawnAngle = Vec2f(spawnPosYaw, spawnPosPitch)
                return worldState
            }
        }
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    class QuantumWorld(val runtimeWorld: RuntimeWorldHandle, val worldData: QuantumWorldData) {
        val serverWorld: ServerWorld
            get() = runtimeWorld.asWorld()
    }

    class QuantumWorldData(worldId: Identifier, dimensionId: Identifier, runtimeWorldConfig: RuntimeWorldConfig) {
        var worldId: Identifier = worldId
            private set
        var dimensionId: Identifier = dimensionId
            private set
        var runtimeWorldConfig: RuntimeWorldConfig = runtimeWorldConfig
            private set

        // Store GameRules settings
        private val gameRulesData: MutableMap<String, String> = mutableMapOf()

        // Store WorldBorder settings
        private val worldBorderData: MutableMap<String, String> = mutableMapOf()

        fun setGameRule(gameRuleName: String, value: String) {
            gameRulesData[gameRuleName] = value
        }

        fun getGameRule(gameRuleName: String): String? {
            return gameRulesData[gameRuleName]
        }

        fun getAllGameRules(): Map<String, String> {
            return gameRulesData.toMap()
        }

        fun setWorldBorderProperty(property: String, value: String) {
            worldBorderData[property] = value
        }

        fun getWorldBorderProperty(property: String): String? {
            return worldBorderData[property]
        }

        fun getAllWorldBorderProperties(): Map<String, String> {
            return worldBorderData.toMap()
        }

        fun writeToNbt(nbt: NbtCompound) {
            nbt.putString("worldId", worldId.toString())
            nbt.putString("dimensionId", dimensionId.toString())
            nbt.putLong("seed", runtimeWorldConfig.seed)
            nbt.putInt("difficulty", runtimeWorldConfig.difficulty.id)
            nbt.putBoolean("tick", runtimeWorldConfig.shouldTickTime())

            // Save GameRules
            val gameRulesNbt = NbtCompound()
            gameRulesData.forEach { (key, value) ->
                gameRulesNbt.putString(key, value)
            }
            nbt.put("gameRules", gameRulesNbt)

            // Save WorldBorder
            val worldBorderNbt = NbtCompound()
            worldBorderData.forEach { (key, value) ->
                worldBorderNbt.putString(key, value)
            }
            nbt.put("worldBorder", worldBorderNbt)
        }

        companion object {
            fun fromNbt(nbt: NbtCompound): QuantumWorldData {
                val worldId = Identifier.of(nbt.getString("worldId"))
                val dimensionId = Identifier.of(nbt.getString("dimensionId"))
                val seed = nbt.getLong("seed")
                val difficulty = Difficulty.byId(nbt.getInt("difficulty"))
                val shouldTick = nbt.getBoolean("tick")

                val worldData = QuantumWorldData(
                    worldId,
                    dimensionId,
                    RuntimeWorldConfig()
                        .setSeed(seed)
                        .setDifficulty(difficulty)
                        .setShouldTickTime(shouldTick)
                )

                // Load GameRules
                if (nbt.contains("gameRules")) {
                    val gameRulesNbt = nbt.getCompound("gameRules")
                    gameRulesNbt.keys.forEach { key ->
                        worldData.setGameRule(key, gameRulesNbt.getString(key))
                    }
                }

                // Load WorldBorder
                if (nbt.contains("worldBorder")) {
                    val worldBorderNbt = nbt.getCompound("worldBorder")
                    worldBorderNbt.keys.forEach { key ->
                        worldData.setWorldBorderProperty(key, worldBorderNbt.getString(key))
                    }
                }

                return worldData
            }
        }
    }

    class QuantumPortalData(destId: Identifier, portalBlockId: Identifier, portalIgniteItemId: Identifier, color: Int) {
        var destinationId: Identifier = destId
            private set
        var portalBlockId: Identifier = portalBlockId
            private set
        var portalIgniteItemId: Identifier = portalIgniteItemId
            private set
        var portalColor: Int = color
            private set

        fun writeToNbt(nbt: NbtCompound) {
            nbt.putString("destinationId", destinationId.toString())
            nbt.putString("blockId", portalBlockId.toString())
            nbt.putString("igniteId", portalIgniteItemId.toString())
            nbt.putInt("color", portalColor)
        }

        companion object {
            fun fromNbt(nbt: NbtCompound): QuantumPortalData {
                val destinationId = Identifier.of(nbt.getString("destinationId"))
                val blockId = Identifier.of(nbt.getString("blockId"))
                val igniteId = Identifier.of(nbt.getString("igniteId"))
                val color = nbt.getInt("color")

                return QuantumPortalData(destinationId, blockId, igniteId, color)
            }
        }
    }
}