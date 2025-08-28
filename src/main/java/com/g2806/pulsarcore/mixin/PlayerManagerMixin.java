package com.g2806.pulsarcore.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void sendWorldBorderOnConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        ServerWorld world = player.getServerWorld();
        // Send the current world's border to the connecting player
        player.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(world.getWorldBorder()));
        System.out.println("[PulsarCore] Sent WorldBorder packet to player " + player.getName().getString() + 
                          " in dimension " + world.getRegistryKey().getValue());
    }

    @Inject(method = "respawnPlayer", at = @At("RETURN"))
    private void sendWorldBorderOnRespawn(ServerPlayerEntity player, boolean alive, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity newPlayer = cir.getReturnValue();
        if (newPlayer != null) {
            ServerWorld world = newPlayer.getServerWorld();
            // Send the respawn world's border
            newPlayer.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(world.getWorldBorder()));
            System.out.println("[PulsarCore] Sent WorldBorder packet to respawned player " + newPlayer.getName().getString() + 
                              " in dimension " + world.getRegistryKey().getValue());
        }
    }
}