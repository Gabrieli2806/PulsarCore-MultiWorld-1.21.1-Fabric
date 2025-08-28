package com.g2806.pulsarcore.worldborder

import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.world.PersistentState
import net.minecraft.world.border.WorldBorder

class PulsarWorldBorderState(
    private var centerX: Double = WorldBorder.DEFAULT_BORDER.centerX,
    private var centerZ: Double = WorldBorder.DEFAULT_BORDER.centerZ,
    private var size: Double = WorldBorder.DEFAULT_BORDER.size,
    private var buffer: Double = WorldBorder.DEFAULT_BORDER.safeZone,
    private var damagePerBlock: Double = WorldBorder.DEFAULT_BORDER.damagePerBlock,
    private var warningBlocks: Int = WorldBorder.DEFAULT_BORDER.warningBlocks,
    private var warningTime: Int = WorldBorder.DEFAULT_BORDER.warningTime
) : PersistentState() {

    constructor() : this(
        WorldBorder.DEFAULT_BORDER.centerX,
        WorldBorder.DEFAULT_BORDER.centerZ,
        WorldBorder.DEFAULT_BORDER.size,
        WorldBorder.DEFAULT_BORDER.safeZone,
        WorldBorder.DEFAULT_BORDER.damagePerBlock,
        WorldBorder.DEFAULT_BORDER.warningBlocks,
        WorldBorder.DEFAULT_BORDER.warningTime
    )

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): NbtCompound {
        nbt.putDouble("BorderCenterX", centerX)
        nbt.putDouble("BorderCenterZ", centerZ)
        nbt.putDouble("BorderSize", size)
        nbt.putDouble("BorderSafeZone", buffer)
        nbt.putDouble("BorderDamagePerBlock", damagePerBlock)
        nbt.putInt("BorderWarningBlocks", warningBlocks)
        nbt.putInt("BorderWarningTime", warningTime)
        return nbt
    }

    fun getCenterX(): Double = centerX
    fun getCenterZ(): Double = centerZ
    fun getSize(): Double = size
    fun getBuffer(): Double = buffer
    fun getDamagePerBlock(): Double = damagePerBlock
    fun getWarningBlocks(): Int = warningBlocks
    fun getWarningTime(): Int = warningTime

    fun fromBorder(worldBorder: WorldBorder) {
        centerX = worldBorder.centerX
        centerZ = worldBorder.centerZ
        size = worldBorder.size
        buffer = worldBorder.safeZone
        damagePerBlock = worldBorder.damagePerBlock
        warningBlocks = worldBorder.warningBlocks
        warningTime = worldBorder.warningTime
        markDirty()
    }

    fun applyToBorder(worldBorder: WorldBorder) {
        worldBorder.setCenter(centerX, centerZ)
        worldBorder.setSize(size)
        worldBorder.safeZone = buffer
        worldBorder.damagePerBlock = damagePerBlock
        worldBorder.warningBlocks = warningBlocks
        worldBorder.warningTime = warningTime
    }

    companion object {
        @JvmStatic
        val TYPE = Type(
            ::PulsarWorldBorderState,
            ::fromNbt,
            null
        )

        @JvmStatic
        fun fromNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): PulsarWorldBorderState {
            return PulsarWorldBorderState(
                nbt.getDouble("BorderCenterX"),
                nbt.getDouble("BorderCenterZ"),
                nbt.getDouble("BorderSize"),
                nbt.getDouble("BorderSafeZone"),
                nbt.getDouble("BorderDamagePerBlock"),
                nbt.getInt("BorderWarningBlocks"),
                nbt.getInt("BorderWarningTime")
            )
        }
    }
}