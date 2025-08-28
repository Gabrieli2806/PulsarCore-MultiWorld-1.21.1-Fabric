package com.g2806.pulsarcore.world

import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.data.PulsarWorldData
import com.g2806.pulsarcore.storage.StorageManager
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.border.WorldBorder
import net.minecraft.world.border.WorldBorderListener

object WorldBorderManager {

    fun applyWorldBorderToWorld(world: ServerWorld, worldData: PulsarWorldData, server: MinecraftServer) {
        val worldBorderProperties = worldData.getAllWorldBorderProperties()
        val isQuantumWorld = world.registryKey.value.namespace == "quantum"
        val isPulsarWorld = world.registryKey.value.namespace == "pulsar"

        if (worldBorderProperties.isEmpty()) {
            PulsarCore.LOGGER.debug("No WorldBorder properties found for world {}", world.registryKey.value)
            
            // Apply default settings for new Quantum/PulsarCore worlds
            if (isQuantumWorld || isPulsarWorld) {
                applyDefaultWorldBorderSettings(world, isQuantumWorld)
                PulsarCore.LOGGER.info("Applied default WorldBorder settings for new {} world: {}", 
                    if (isQuantumWorld) "Quantum" else "PulsarCore", world.registryKey.value)
            }
            return
        }

        worldBorderProperties.forEach { (property, value) ->
            try {
                applySpecificWorldBorderProperty(world, property, value, worldData)
            } catch (e: Exception) {
                PulsarCore.LOGGER.warn("Failed to apply WorldBorder property {} with value {} to world {}: {}",
                    property, value, world.registryKey.value, e.message)
            }
        }
    }

    private fun applyDefaultWorldBorderSettings(world: ServerWorld, isQuantumWorld: Boolean) {
        val border = world.worldBorder
        border.setCenter(0.0, 0.0)
        border.setSize(if (isQuantumWorld) 1000.0 else 2000.0)
        border.damagePerBlock = 0.2
        border.safeZone = 5.0
        border.warningTime = 15
        border.warningBlocks = 5
    }

    private fun applySpecificWorldBorderProperty(world: ServerWorld, property: String, value: String, worldData: PulsarWorldData) {
        try {
            val border = world.worldBorder

            when (property) {
                "centerX" -> {
                    val centerZ = worldData.getWorldBorderProperty("centerZ")?.toDoubleOrNull() ?: border.centerZ
                    border.setCenter(value.toDouble(), centerZ)
                    PulsarCore.LOGGER.debug("Applied WorldBorder center X: {} for world {}", value, world.registryKey.value)
                }
                "centerZ" -> {
                    val centerX = worldData.getWorldBorderProperty("centerX")?.toDoubleOrNull() ?: border.centerX
                    border.setCenter(centerX, value.toDouble())
                    PulsarCore.LOGGER.debug("Applied WorldBorder center Z: {} for world {}", value, world.registryKey.value)
                }
                "size" -> {
                    border.setSize(value.toDouble())
                    PulsarCore.LOGGER.debug("Applied WorldBorder size: {} for world {}", value, world.registryKey.value)
                }
                "damagePerBlock" -> {
                    border.damagePerBlock = value.toDouble()
                    PulsarCore.LOGGER.debug("Applied WorldBorder damage: {} for world {}", value, world.registryKey.value)
                }
                "safeZone" -> {
                    border.safeZone = value.toDouble()
                    PulsarCore.LOGGER.debug("Applied WorldBorder safe zone: {} for world {}", value, world.registryKey.value)
                }
                "warningTime" -> {
                    border.warningTime = value.toInt()
                    PulsarCore.LOGGER.debug("Applied WorldBorder warning time: {} for world {}", value, world.registryKey.value)
                }
                "warningBlocks" -> {
                    border.warningBlocks = value.toInt()
                    PulsarCore.LOGGER.debug("Applied WorldBorder warning blocks: {} for world {}", value, world.registryKey.value)
                }
                else -> PulsarCore.LOGGER.warn("Unknown WorldBorder property: {}", property)
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to apply WorldBorder property {} with value {}: {}", property, value, e.message)
        }
    }

    fun setupWorldBorderListener(world: ServerWorld) {
        val border = world.worldBorder
        border.addListener(PulsarWorldBorderListener(world))
        PulsarCore.LOGGER.debug("Setup WorldBorder listener for world {}", world.registryKey.value)
    }

    fun sendWorldBorderUpdateToPlayers(world: ServerWorld) {
        val border = world.worldBorder
        val players = world.players

        // Send complete world border info to all players in this world
        players.forEach { player ->
            player.networkHandler.sendPacket(WorldBorderInitializeS2CPacket(border))
        }
        PulsarCore.LOGGER.debug("Sent WorldBorder update to {} players in world {} (Size: {}, Center: {}, {})", 
            players.size, world.registryKey.value, border.size, border.centerX, border.centerZ)
    }

    fun forceWorldBorderUpdateForPlayer(player: net.minecraft.server.network.ServerPlayerEntity) {
        val world = player.serverWorld
        val border = world.worldBorder
        
        // Force send the worldborder packet to ensure client sees the correct border
        player.networkHandler.sendPacket(WorldBorderInitializeS2CPacket(border))
        PulsarCore.LOGGER.debug("Force-sent WorldBorder update to player {} in dimension {} (Size: {}, Center: {}, {})",
            player.name.string, world.registryKey.value, border.size, border.centerX, border.centerZ)
    }

    fun saveWorldBorderState(world: ServerWorld) {
        try {
            val border = world.worldBorder
            val isQuantumWorld = world.registryKey.value.namespace == "quantum"
            val isPulsarWorld = world.registryKey.value.namespace == "pulsar"

            // For Quantum/PulsarCore worlds, use both PersistentState and PulsarStorage
            if (isQuantumWorld || isPulsarWorld) {
                // Save to PersistentState (for mixin compatibility)
                val stateId = "pulsarcore_worldborder_" + world.registryKey.value.toString().replace(":", "_")
                val stateManager = world.persistentStateManager
                val borderState = stateManager.getOrCreate(
                    com.g2806.pulsarcore.worldborder.PulsarWorldBorderState.TYPE, 
                    stateId
                )
                borderState.fromBorder(border)
                
                PulsarCore.LOGGER.debug("Saved WorldBorder PersistentState for {} world: {}", 
                    if (isQuantumWorld) "Quantum" else "PulsarCore", world.registryKey.value)
            }

            // Also save to PulsarStorage (for compatibility with existing PulsarCore system)
            val pulsarStorage = StorageManager.getPulsarState(world.server)
            val worldData = pulsarStorage.getWorlds().find { it.worldId == world.registryKey.value }

            if (worldData != null) {
                // Save all current WorldBorder properties
                worldData.setWorldBorderProperty("centerX", border.centerX.toString())
                worldData.setWorldBorderProperty("centerZ", border.centerZ.toString())
                worldData.setWorldBorderProperty("size", border.size.toString())
                worldData.setWorldBorderProperty("damagePerBlock", border.damagePerBlock.toString())
                worldData.setWorldBorderProperty("safeZone", border.safeZone.toString())
                worldData.setWorldBorderProperty("warningTime", border.warningTime.toString())
                worldData.setWorldBorderProperty("warningBlocks", border.warningBlocks.toString())

                pulsarStorage.markDirty()
                PulsarCore.LOGGER.debug("Saved complete WorldBorder state for world {}", world.registryKey.value)
            }
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to save WorldBorder state for world {}: {}", world.registryKey.value, e.message)
        }
    }

    class PulsarWorldBorderListener(private val world: ServerWorld) : WorldBorderListener {
        override fun onSizeChange(border: WorldBorder, size: Double) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderSizeChangedS2CPacket(border))
            }
            // Auto-save when size changes
            saveWorldBorderProperty(world, "size", size.toString())
            PulsarCore.LOGGER.debug("WorldBorder size changed to {} in world {}", size, world.registryKey.value)
        }

        override fun onInterpolateSize(border: WorldBorder, fromSize: Double, toSize: Double, time: Long) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderInitializeS2CPacket(border))
            }
            PulsarCore.LOGGER.debug("WorldBorder size interpolating from {} to {} over {}ms in world {}", fromSize, toSize, time, world.registryKey.value)
        }

        override fun onCenterChanged(border: WorldBorder, centerX: Double, centerZ: Double) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderCenterChangedS2CPacket(border))
            }
            // Auto-save when center changes
            saveWorldBorderProperty(world, "centerX", centerX.toString())
            saveWorldBorderProperty(world, "centerZ", centerZ.toString())
            PulsarCore.LOGGER.debug("WorldBorder center changed to {},{} in world {}", centerX, centerZ, world.registryKey.value)
        }

        override fun onWarningTimeChanged(border: WorldBorder, warningTime: Int) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderWarningTimeChangedS2CPacket(border))
            }
            // Auto-save when warning time changes
            saveWorldBorderProperty(world, "warningTime", warningTime.toString())
            PulsarCore.LOGGER.debug("WorldBorder warning time changed to {} in world {}", warningTime, world.registryKey.value)
        }

        override fun onWarningBlocksChanged(border: WorldBorder, warningBlockDistance: Int) {
            world.players.forEach { player ->
                player.networkHandler.sendPacket(WorldBorderWarningBlocksChangedS2CPacket(border))
            }
            // Auto-save when warning blocks change
            saveWorldBorderProperty(world, "warningBlocks", warningBlockDistance.toString())
            PulsarCore.LOGGER.debug("WorldBorder warning blocks changed to {} in world {}", warningBlockDistance, world.registryKey.value)
        }

        override fun onDamagePerBlockChanged(border: WorldBorder, damagePerBlock: Double) {
            // No packet needed for damage per block changes, but save it
            saveWorldBorderProperty(world, "damagePerBlock", damagePerBlock.toString())
            PulsarCore.LOGGER.debug("WorldBorder damage per block changed to {} in world {}", damagePerBlock, world.registryKey.value)
        }

        override fun onSafeZoneChanged(border: WorldBorder, safeZoneRadius: Double) {
            // No packet needed for safe zone changes, but save it
            saveWorldBorderProperty(world, "safeZone", safeZoneRadius.toString())
            PulsarCore.LOGGER.debug("WorldBorder safe zone changed to {} in world {}", safeZoneRadius, world.registryKey.value)
        }

        private fun saveWorldBorderProperty(world: ServerWorld, property: String, value: String) {
            try {
                val pulsarStorage = StorageManager.getPulsarState(world.server)
                val worldData = pulsarStorage.getWorlds().find { it.worldId == world.registryKey.value }

                if (worldData != null) {
                    worldData.setWorldBorderProperty(property, value)
                    pulsarStorage.markDirty()
                }
            } catch (e: Exception) {
                PulsarCore.LOGGER.error("Failed to auto-save WorldBorder property {} for world {}: {}", property, world.registryKey.value, e.message)
            }
        }
    }
}