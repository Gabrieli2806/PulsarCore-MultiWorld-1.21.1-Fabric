package com.g2806.pulsarcore.mixin;

import com.g2806.pulsarcore.worldborder.BorderWithWorld;
import net.minecraft.network.packet.s2c.play.WorldBorderInitializeS2CPacket;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldBorderInitializeS2CPacket.class)
public abstract class WorldBorderInitializeS2CPacketMixin {
    
    @Redirect(
            method = "<init>(Lnet/minecraft/world/border/WorldBorder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;getCenterX()D")
    )
    private double scaleCenterX(WorldBorder worldBorder) {
        if (!(worldBorder instanceof BorderWithWorld)) {
            return worldBorder.getCenterX();
        }
        
        World world = ((BorderWithWorld) worldBorder).getWorld();
        final double centerX = worldBorder.getCenterX();
        
        // Skip scaling for client or null world
        if (world == null || world.isClient()) {
            return centerX;
        }
        
        // Apply coordinate scaling for different dimensions
        double coordinateScale = world.getDimension().coordinateScale();
        return centerX * coordinateScale;
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/world/border/WorldBorder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;getCenterZ()D")
    )
    private double scaleCenterZ(WorldBorder worldBorder) {
        if (!(worldBorder instanceof BorderWithWorld)) {
            return worldBorder.getCenterZ();
        }
        
        World world = ((BorderWithWorld) worldBorder).getWorld();
        final double centerZ = worldBorder.getCenterZ();
        
        // Skip scaling for client or null world
        if (world == null || world.isClient()) {
            return centerZ;
        }
        
        // Apply coordinate scaling for different dimensions  
        double coordinateScale = world.getDimension().coordinateScale();
        return centerZ * coordinateScale;
    }
}