package com.g2806.pulsarcore.world

import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.data.PulsarWorld
import com.g2806.pulsarcore.data.PulsarWorldData
import com.g2806.pulsarcore.storage.StorageManager
import net.minecraft.world.TeleportTarget
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameRules
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeWorldConfig
import java.util.concurrent.ConcurrentHashMap

object WorldManager {
    private val WORLDS: MutableMap<Identifier, PulsarWorld> = ConcurrentHashMap()

    fun worldExists(identifier: Identifier): Boolean {
        return WORLDS.containsKey(identifier)
    }

    fun getOrCreateWorld(server: MinecraftServer, worldData: PulsarWorldData, saveToDisk: Boolean): PulsarWorld? {
        if (WORLDS.containsKey(worldData.worldId))
            return WORLDS[worldData.worldId]

        val fantasy = Fantasy.get(server)
        val runtimeWorldConfig = worldData.runtimeWorldConfig.setDimensionAndGenerator(server, worldData)
        val runtimeWorldHandle = fantasy.getOrOpenPersistentWorld(worldData.worldId, runtimeWorldConfig)

        val world = PulsarWorld(runtimeWorldHandle, worldData)
        WORLDS[worldData.worldId] = world

        // Setup WorldBorder listener for new worlds
        WorldBorderManager.setupWorldBorderListener(world.serverWorld)

        if (saveToDisk)
            StorageManager.getPulsarState(server).addWorld(worldData)

        return world
    }

    fun deleteWorld(identifier: Identifier): Boolean {
        val world = WORLDS.getOrDefault(identifier, null) ?: return false
        val server = world.serverWorld.server
        val fantasy = Fantasy.get(world.serverWorld.server)

        if (!fantasy.tickDeleteWorld(world.serverWorld))
            return false

        val state = StorageManager.getPulsarState(server)
        state.removeWorld(world.worldData)
        WORLDS.remove(identifier)

        return true
    }

    fun loadWorlds(server: MinecraftServer) {
        WORLDS.clear()
        val state = StorageManager.getPulsarState(server)

        for (worldData in state.getWorlds()) {
            val createdWorld = getOrCreateWorld(server, worldData, false)

            // Apply all saved GameRules to the world
            if (createdWorld != null) {
                PulsarCore.LOGGER.info("Loading world '{}' with {} saved GameRules...", worldData.worldId, worldData.getAllGameRules().size)

                // Log which GameRules are being loaded for debugging
                worldData.getAllGameRules().forEach { (key, value) ->
                    PulsarCore.LOGGER.debug("Loading GameRule: {} = {}", key, value)
                }

                GameRuleManager.applyGameRulesToWorld(createdWorld.serverWorld, worldData, server)
                WorldBorderManager.applyWorldBorderToWorld(createdWorld.serverWorld, worldData, server)
                PulsarCore.LOGGER.info("Successfully loaded world '{}'.", worldData.worldId)
            }
        }
    }

    fun setupDefaultWorldSettings(createdWorld: PulsarWorld?, server: MinecraftServer) {
        createdWorld?.serverWorld?.let { world ->
            // Set some sensible defaults
            world.gameRules.get(GameRules.KEEP_INVENTORY).set(false, server)
            createdWorld.worldData.setGameRule("keepinventory", "false")

            // Set default WorldBorder (smaller than default for custom worlds)
            world.worldBorder.setCenter(0.0, 0.0)
            world.worldBorder.setSize(1000.0) // 1000 blocks by default instead of 60M
            createdWorld.worldData.setWorldBorderProperty("centerX", "0.0")
            createdWorld.worldData.setWorldBorderProperty("centerZ", "0.0")
            createdWorld.worldData.setWorldBorderProperty("size", "1000.0")

            // Setup WorldBorder listener for immediate updates
            WorldBorderManager.setupWorldBorderListener(world)

            // Mark storage as dirty to save the new world data
            StorageManager.getPulsarState(server).markDirty()
        }
    }

    fun setWorldSpawn(world: ServerWorld, player: ServerPlayerEntity, radius: Int, server: MinecraftServer) {
        if (radius >= 0)
            world.gameRules.get(GameRules.SPAWN_RADIUS).set(radius, server)

        world.setCustomSpawnPos(player.pos, player.yaw, player.pitch)
    }

    fun teleportPlayerToWorld(player: PlayerEntity, targetWorld: ServerWorld) {
        val worldState = StorageManager.getWorldState(targetWorld)
        val teleportTarget = TeleportTarget(
            targetWorld, 
            worldState.worldSpawnPos,
            Vec3d.ZERO, 
            worldState.worldSpawnAngle.x, 
            worldState.worldSpawnAngle.y
        ) { /* Post dimension transition callback */ }
        player.teleportTo(teleportTarget)
    }

    // Extension function
    private fun ServerWorld.setCustomSpawnPos(pos: Vec3d, yaw: Float, pitch: Float) {
        val worldState = StorageManager.getWorldState(this)
        worldState.setWorldSpawn(pos, yaw, pitch)
    }

    // Extension function for RuntimeWorldConfig
    private fun RuntimeWorldConfig.setDimensionAndGenerator(server: MinecraftServer, worldData: PulsarWorldData): RuntimeWorldConfig {
        val worldRegKey = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, worldData.dimensionId)
        var dimensionWorld = server.getWorld(worldRegKey)

        if (dimensionWorld == null) {
            PulsarCore.LOGGER.error("Failed to retrieve dimension ${worldData.dimensionId}. Defaulting to minecraft:overworld")
            dimensionWorld = server.overworld
        }

        worldData.runtimeWorldConfig.setDimensionType(dimensionWorld!!.dimensionEntry)
            .setGenerator(dimensionWorld.chunkManager.chunkGenerator)
        return this
    }
}