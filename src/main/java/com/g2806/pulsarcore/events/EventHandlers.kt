package com.g2806.pulsarcore.events

import com.g2806.pulsarcore.storage.StorageManager
import com.g2806.pulsarcore.world.WorldManager
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.*
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.world.World

object EventHandlers {

    fun registerEvents() {
        UseBlockCallback.EVENT.register(::handlePlayerUseSign)
        ServerPlayerEvents.AFTER_RESPAWN.register(::handlePlayerRespawn)
    }

    private fun handlePlayerRespawn(oldPlayer: ServerPlayerEntity, newPlayer: ServerPlayerEntity, alive: Boolean) {
        if (oldPlayer.spawnPointPosition != null) return

        val worldState = StorageManager.getWorldState(newPlayer.serverWorld)
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
                WorldManager.teleportPlayerToWorld(player, targetWorld)
                return ActionResult.SUCCESS
            }
        }

        return ActionResult.PASS
    }
}