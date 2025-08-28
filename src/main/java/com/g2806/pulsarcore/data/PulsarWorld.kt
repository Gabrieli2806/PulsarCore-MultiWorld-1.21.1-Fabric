package com.g2806.pulsarcore.data

import net.minecraft.server.world.ServerWorld
import xyz.nucleoid.fantasy.RuntimeWorldHandle

class PulsarWorld(val runtimeWorld: RuntimeWorldHandle, val worldData: PulsarWorldData) {
    val serverWorld: ServerWorld
        get() = runtimeWorld.asWorld()
}