package com.g2806.pulsarcore.storage

import com.g2806.pulsarcore.PulsarCore
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d

object StorageManager {

    fun getPulsarState(server: MinecraftServer): PulsarStorage {
        val stateManager = server.overworld.persistentStateManager
        val pulsarState = stateManager.getOrCreate(PulsarStorage.PersistentStateTypeLoader, PulsarCore.MOD_ID)
        pulsarState.markDirty()
        return pulsarState
    }

    fun getWorldState(world: ServerWorld): PulsarWorldStorage {
        val worldState = world.persistentStateManager.getOrCreate(PulsarWorldStorage.PersistentStateTypeLoader, "${PulsarCore.MOD_ID}_world")
        if (worldState.worldSpawnPos == Vec3d.ZERO)
            worldState.setWorldSpawn(world.spawnPos.toBottomCenterPos(), 0f, 0f)
        worldState.markDirty()
        return worldState
    }

    fun saveGameRuleToWorldData(world: ServerWorld, gameRuleName: String, value: String) {
        try {
            val pulsarStorage = getPulsarState(world.server)
            val worldData = pulsarStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                worldData.setGameRule(gameRuleName, value)
                pulsarStorage.markDirty()
                PulsarCore.LOGGER.info("Saved GameRule '{}' = '{}' for world '{}'", gameRuleName, value, world.registryKey.value)
            } else {
                PulsarCore.LOGGER.warn("Could not find world data for '{}' to save GameRule '{}'", world.registryKey.value, gameRuleName)
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to save GameRule '{}' for world '{}': {}", gameRuleName, world.registryKey.value, e.message)
        }
    }

    fun saveWorldBorderToWorldData(world: ServerWorld, property: String, value: String) {
        try {
            val pulsarStorage = getPulsarState(world.server)
            val worldData = pulsarStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                worldData.setWorldBorderProperty(property, value)
                pulsarStorage.markDirty()
                PulsarCore.LOGGER.info("Saved WorldBorder '{}' = '{}' for world '{}'", property, value, world.registryKey.value)
            } else {
                PulsarCore.LOGGER.warn("Could not find world data for '{}' to save WorldBorder property '{}'", world.registryKey.value, property)
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to save WorldBorder property '{}' for world '{}': {}", property, world.registryKey.value, e.message)
        }
    }
}