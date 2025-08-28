package com.g2806.pulsarcore.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.storage.StorageManager
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.world.GameRules

object GameRuleCommands {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        registerBooleanGameRules(dispatcher)
        registerIntegerGameRules(dispatcher)
        registerGameRuleHelpCommand(dispatcher)
    }

    private fun registerBooleanGameRules(dispatcher: CommandDispatcher<ServerCommandSource>) {
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

        booleanGameRules.forEach { (name, gameRule) ->
            dispatcher.register(CommandManager.literal("pcg")
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
    }

    private fun registerIntegerGameRules(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val integerGameRules = mapOf(
            "randomtickspeed" to GameRules.RANDOM_TICK_SPEED,
            "spawnradius" to GameRules.SPAWN_RADIUS,
            "maxentitycramming" to GameRules.MAX_ENTITY_CRAMMING,
            "maxcommandchainlength" to GameRules.MAX_COMMAND_CHAIN_LENGTH,
            "commandmodificationblocklimit" to GameRules.COMMAND_MODIFICATION_BLOCK_LIMIT
        )

        integerGameRules.forEach { (name, gameRule) ->
            dispatcher.register(CommandManager.literal("pcg")
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

    private fun registerGameRuleHelpCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(CommandManager.literal("pcg")
            .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
            .executes(::executePcgHelp)
        )
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
                    StorageManager.saveGameRuleToWorldData(world, ruleName, value.toString())
                }
                is GameRules.IntRule -> {
                    val intValue = if (value) 100 else 0 // Convert boolean to int for special cases
                    rule.set(intValue, context.source.server)
                    StorageManager.saveGameRuleToWorldData(world, ruleName, value.toString()) // Save original boolean value
                }
                else -> {
                    context.source.sendError(Text.literal("§cUnsupported rule type for $ruleName"))
                    return 0
                }
            }

            val status = if (value) "enabled" else "disabled"
            context.source.sendMessage(Text.literal("§aGameRule §6$ruleName §aset to §6$status §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while setting game rule $ruleName.", e)
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
            PulsarCore.LOGGER.error("An error occurred while getting game rule $ruleName.", e)
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
                    StorageManager.saveGameRuleToWorldData(world, ruleName, value.toString())
                }
                else -> {
                    context.source.sendError(Text.literal("§cRule $ruleName is not an integer rule"))
                    return 0
                }
            }

            context.source.sendMessage(Text.literal("§aGameRule §6$ruleName §aset to §6$value §ain world §6${world.registryKey.value}"))

        } catch (e: Exception) {
            PulsarCore.LOGGER.error("An error occurred while setting game rule $ruleName.", e)
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
            PulsarCore.LOGGER.error("An error occurred while getting game rule $ruleName.", e)
        }
        return 1
    }

    private fun executePcgHelp(context: CommandContext<ServerCommandSource>): Int {
        if (context.source == null) return 0

        val source = context.source
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6═══════════════ §ePulsarCore GameRules §6═══════════════"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Mechanics §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg dofiretick <true|false> §8- Fire spread"))
        source.sendMessage(Text.literal("§7  /pcg domobloot <true|false> §8- Mob drops"))
        source.sendMessage(Text.literal("§7  /pcg domobspawning <true|false> §8- Mob spawning"))
        source.sendMessage(Text.literal("§7  /pcg dotiledrops <true|false> §8- Block drops"))
        source.sendMessage(Text.literal("§7  /pcg doentitydrops <true|false> §8- Entity drops"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eWorld Cycles §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg dodaylightcycle <true|false> §8- Day/night cycle"))
        source.sendMessage(Text.literal("§7  /pcg doweathercycle <true|false> §8- Weather cycle"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eDamage Types §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg drowningdamage <true|false> §8- Drowning damage"))
        source.sendMessage(Text.literal("§7  /pcg falldamage <true|false> §8- Fall damage"))
        source.sendMessage(Text.literal("§7  /pcg firedamage <true|false> §8- Fire damage"))
        source.sendMessage(Text.literal("§7  /pcg freezedamage <true|false> §8- Freeze damage"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eSpecial Spawns §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg dopatrolspawning <true|false> §8- Pillager patrols"))
        source.sendMessage(Text.literal("§7  /pcg dotraderSpawning <true|false> §8- Wandering traders"))
        source.sendMessage(Text.literal("§7  /pcg dowardenspawning <true|false> §8- Warden spawning"))
        source.sendMessage(Text.literal("§7  /pcg doinsomniaPhantoms <true|false> §8- Insomnia phantoms"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §ePlayer Mechanics §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg keepinventory <true|false> §8- Keep inventory on death"))
        source.sendMessage(Text.literal("§7  /pcg naturalregeneration <true|false> §8- Natural regeneration"))
        source.sendMessage(Text.literal("§7  /pcg doimmediaterespawn <true|false> §8- Immediate respawn"))
        source.sendMessage(Text.literal("§7  /pcg announceadvancements <true|false> §8- Announce advancements"))
        source.sendMessage(Text.literal("§7  /pcg doLimitedCrafting <true|false> §8- Limited crafting"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eSystem §7(Boolean):"))
        source.sendMessage(Text.literal("§7  /pcg commandblockoutput <true|false> §8- Command block output"))
        source.sendMessage(Text.literal("§7  /pcg logadmincommands <true|false> §8- Log admin commands"))
        source.sendMessage(Text.literal("§7  /pcg showdeathmessages <true|false> §8- Death messages"))
        source.sendMessage(Text.literal("§7  /pcg sendcommandfeedback <true|false> §8- Command feedback"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eNumerical Settings §7(Integer):"))
        source.sendMessage(Text.literal("§7  /pcg randomtickspeed <number> §8- Random tick speed"))
        source.sendMessage(Text.literal("§7  /pcg spawnradius <number> §8- Spawn radius"))
        source.sendMessage(Text.literal("§7  /pcg maxentitycramming <number> §8- Max entity cramming"))
        source.sendMessage(Text.literal("§7  /pcg maxcommandchainlength <number> §8- Max command chain length"))
        source.sendMessage(Text.literal("§7  /pcg commandmodificationblocklimit <number> §8- Command block limit"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§6▌ §eOther Settings §7(Mixed):"))
        source.sendMessage(Text.literal("§7  /pcg playerssleepingpercentage <true|false> §8- Sleep percentage"))
        source.sendMessage(Text.literal("§7  /pcg snowaccumulationheight <true|false> §8- Snow accumulation"))
        source.sendMessage(Text.literal("§7  /pcg waterSourceConversion <true|false> §8- Water source conversion"))
        source.sendMessage(Text.literal("§7  /pcg lavaSourceConversion <true|false> §8- Lava source conversion"))
        source.sendMessage(Text.literal(""))
        source.sendMessage(Text.literal("§c▌ §7Usage: Command without value shows current setting"))
        source.sendMessage(Text.literal("§6═══════════════════════════════════════════════════"))

        return 1
    }
}