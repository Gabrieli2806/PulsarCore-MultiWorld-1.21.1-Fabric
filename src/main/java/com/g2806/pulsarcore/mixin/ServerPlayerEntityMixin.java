package com.g2806.pulsarcore.mixin;

import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "teleportTo", at = @At("RETURN"))
    private void sendWorldBorderOnDimensionChange(TeleportTarget teleportTarget, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        ServerPlayerEntity player = cir.getReturnValue();
        if (player != null) {
            ServerWorld newWorld = player.getServerWorld();
            
            // Send the new world's border to the player after teleportation
            player.networkHandler.sendPacket(new WorldBorderInitializeS2CPacket(newWorld.getWorldBorder()));
            System.out.println("[PulsarCore] Sent WorldBorder packet to teleported player " + player.getName().getString() + 
                              " in new dimension " + newWorld.getRegistryKey().getValue() + 
                              " (Size: " + newWorld.getWorldBorder().getSize() + 
                              ", Center: " + newWorld.getWorldBorder().getCenterX() + "," + newWorld.getWorldBorder().getCenterZ() + ")");
        }
    }
}