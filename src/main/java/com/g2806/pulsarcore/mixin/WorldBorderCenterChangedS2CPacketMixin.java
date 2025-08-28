package com.g2806.pulsarcore.mixin;

import net.minecraft.network.packet.s2c.play.WorldBorderCenterChangedS2CPacket;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.g2806.pulsarcore.worldborder.BorderWithWorld;

@Mixin(WorldBorderCenterChangedS2CPacket.class)
public abstract class WorldBorderCenterChangedS2CPacketMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/world/border/WorldBorder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;getCenterX()D")
    )
    private double scaleCenterX(WorldBorder worldBorder) {
        World world = ((BorderWithWorld) worldBorder).getWorld();

        final double centerX = worldBorder.getCenterX();
        return world == null || world.isClient ? centerX : centerX * world.getDimension().coordinateScale();
    }

    @Redirect(
            method = "<init>(Lnet/minecraft/world/border/WorldBorder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;getCenterZ()D")
    )
    private double scaleCenterZ(WorldBorder worldBorder) {
        World world = ((BorderWithWorld) worldBorder).getWorld();

        final double centerZ = worldBorder.getCenterZ();
        return world == null || world.isClient ? centerZ : centerZ * world.getDimension().coordinateScale();
    }
}