package com.g2806.pulsarcore.worldborder

import com.g2806.pulsarcore.PulsarCore
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.border.WorldBorder
import net.minecraft.world.border.WorldBorderListener

class PerWorldBorderListener(private val world: ServerWorld) : WorldBorderListener {

    override fun onSizeChange(border: WorldBorder, size: Double) {
        world.players.forEach { player ->
            player.networkHandler.sendPacket(WorldBorderSizeChangedS2CPacket(border))
        }
        saveBorderState(border)
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
        saveBorderState(border)
        PulsarCore.LOGGER.debug("WorldBorder center changed to {},{} in world {}", centerX, centerZ, world.registryKey.value)
    }

    override fun onWarningTimeChanged(border: WorldBorder, warningTime: Int) {
        world.players.forEach { player ->
            player.networkHandler.sendPacket(WorldBorderWarningTimeChangedS2CPacket(border))
        }
        saveBorderState(border)
        PulsarCore.LOGGER.debug("WorldBorder warning time changed to {} in world {}", warningTime, world.registryKey.value)
    }

    override fun onWarningBlocksChanged(border: WorldBorder, warningBlockDistance: Int) {
        world.players.forEach { player ->
            player.networkHandler.sendPacket(WorldBorderWarningBlocksChangedS2CPacket(border))
        }
        saveBorderState(border)
        PulsarCore.LOGGER.debug("WorldBorder warning blocks changed to {} in world {}", warningBlockDistance, world.registryKey.value)
    }

    override fun onDamagePerBlockChanged(border: WorldBorder, damagePerBlock: Double) {
        saveBorderState(border)
        PulsarCore.LOGGER.debug("WorldBorder damage per block changed to {} in world {}", damagePerBlock, world.registryKey.value)
    }

    override fun onSafeZoneChanged(border: WorldBorder, safeZoneRadius: Double) {
        saveBorderState(border)
        PulsarCore.LOGGER.debug("WorldBorder safe zone changed to {} in world {}", safeZoneRadius, world.registryKey.value)
    }

    private fun saveBorderState(border: WorldBorder) {
        try {
            val stateManager = world.persistentStateManager
            
            // Generate unique state ID for ALL non-Overworld dimensions
            val stateId = if (world.registryKey.value.toString() == "minecraft:overworld") {
                "pulsarcore_worldborder_overworld" // Even Overworld gets unique ID for consistency
            } else {
                "pulsarcore_worldborder_" + world.registryKey.value.toString().replace(":", "_")
            }
            
            val borderState = stateManager.getOrCreate(PulsarWorldBorderState.TYPE, stateId)
            borderState.fromBorder(border)
            
            PulsarCore.LOGGER.debug("Saved WorldBorder state for dimension {} using state ID: {}", 
                world.registryKey.value, stateId)
        } catch (e: Exception) {
            PulsarCore.LOGGER.error("Failed to save WorldBorder state for dimension {}: {}", world.registryKey.value, e.message)
        }
    }
}