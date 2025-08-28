package com.g2806.pulsarcore.mixin;

import com.g2806.pulsarcore.worldborder.BorderWithWorld;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin implements BorderWithWorld {
    @Unique
    private World world;

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public void setWorld(World world) {
        this.world = world;
    }
}