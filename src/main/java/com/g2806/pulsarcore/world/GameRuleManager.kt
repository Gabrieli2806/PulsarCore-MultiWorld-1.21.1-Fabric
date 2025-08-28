package com.g2806.pulsarcore.world

import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.data.PulsarWorldData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.GameRules

object GameRuleManager {

    fun applyGameRulesToWorld(world: ServerWorld, worldData: PulsarWorldData, server: MinecraftServer) {
        val gameRules = worldData.getAllGameRules()

        gameRules.forEach { (gameRuleName, value) ->
            try {
                applySpecificGameRule(world, gameRuleName, value, server)
            } catch (e: Exception) {
                PulsarCore.LOGGER.warn("Failed to apply GameRule {} with value {} to world {}: {}",
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
                else -> PulsarCore.LOGGER.warn("Unknown GameRule: {}", gameRuleName)
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to apply GameRule {} with value {}: {}", gameRuleName, value, e.message)
        }
    }
}