package com.g2806.pulsarcore.storage

import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper.WrapperLookup
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.world.PersistentState

class PulsarWorldStorage : PersistentState() {
    var worldSpawnPos: Vec3d = Vec3d.ZERO
        private set
    var worldSpawnAngle = Vec2f(0.0f, 0.0f) // X is yaw, Y is pitch
        private set

    fun setWorldSpawn(worldSpawn: Vec3d, worldSpawnYaw: Float, worldSpawnPitch: Float) {
        this.worldSpawnPos = worldSpawn
        this.worldSpawnAngle = Vec2f(worldSpawnYaw, worldSpawnPitch)
        markDirty()
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: WrapperLookup): NbtCompound {
        // write spawn pos
        nbt.putDouble("spawnposx", worldSpawnPos.x)
        nbt.putDouble("spawnposy", worldSpawnPos.y)
        nbt.putDouble("spawnposz", worldSpawnPos.z)

        // write spawn angle
        nbt.putFloat("spawnposyaw", worldSpawnAngle.x)
        nbt.putFloat("spawnpospitch", worldSpawnAngle.y)
        return nbt
    }

    companion object {
        val PersistentStateTypeLoader = PersistentState.Type(
            { PulsarWorldStorage() },
            { nbt: NbtCompound, registryLookup: WrapperLookup -> fromNbt(nbt, registryLookup) },
            null
        )

        @Suppress("UNUSED_PARAMETER")
        fun fromNbt(nbt: NbtCompound, registryLookup: WrapperLookup): PulsarWorldStorage {
            val worldState = PulsarWorldStorage()

            // get spawn pos
            val spawnPosX = nbt.getDouble("spawnposx")
            val spawnPosY = nbt.getDouble("spawnposy")
            val spawnPosZ = nbt.getDouble("spawnposz")

            // get spawn angle
            val spawnPosYaw = nbt.getFloat("spawnposyaw")
            val spawnPosPitch = nbt.getFloat("spawnpospitch")

            worldState.worldSpawnPos = Vec3d(spawnPosX, spawnPosY, spawnPosZ)
            worldState.worldSpawnAngle = Vec2f(spawnPosYaw, spawnPosPitch)
            return worldState
        }
    }
}