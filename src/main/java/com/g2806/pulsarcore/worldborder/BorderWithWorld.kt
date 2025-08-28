package com.g2806.pulsarcore.worldborder

import net.minecraft.world.World

interface BorderWithWorld {
    fun getWorld(): World?
    fun setWorld(world: World)
}