package com.g2806.pulsarcore.data

import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier

class PulsarPortalData(destId: Identifier, portalBlockId: Identifier, portalIgniteItemId: Identifier, color: Int) {
    var destinationId: Identifier = destId
        private set
    var portalBlockId: Identifier = portalBlockId
        private set
    var portalIgniteItemId: Identifier = portalIgniteItemId
        private set
    var portalColor: Int = color
        private set

    fun writeToNbt(nbt: NbtCompound) {
        nbt.putString("destinationId", destinationId.toString())
        nbt.putString("blockId", portalBlockId.toString())
        nbt.putString("igniteId", portalIgniteItemId.toString())
        nbt.putInt("color", portalColor)
    }

    companion object {
        fun fromNbt(nbt: NbtCompound): PulsarPortalData {
            val destinationId = Identifier.of(nbt.getString("destinationId"))
            val blockId = Identifier.of(nbt.getString("blockId"))
            val igniteId = Identifier.of(nbt.getString("igniteId"))
            val color = nbt.getInt("color")

            return PulsarPortalData(destinationId, blockId, igniteId, color)
        }
    }
}