package com.g2806.pulsarcore.storage

import com.g2806.pulsarcore.data.PulsarPortalData
import com.g2806.pulsarcore.data.PulsarWorldData
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper.WrapperLookup
import net.minecraft.world.PersistentState

class PulsarStorage : PersistentState() {
    private val worlds: MutableList<PulsarWorldData> = ArrayList()
    private val portals: MutableList<PulsarPortalData> = ArrayList()

    fun getWorlds(): List<PulsarWorldData> = worlds
    fun getPortals(): List<PulsarPortalData> = portals
    fun getPortal(predicate: (PulsarPortalData) -> Boolean): PulsarPortalData? = portals.find(predicate)

    fun addWorld(worldData: PulsarWorldData) {
        worlds.add(worldData)
        markDirty()
    }

    fun addPortal(portalData: PulsarPortalData) {
        portals.add(portalData)
        markDirty()
    }

    fun removeWorld(worldData: PulsarWorldData) {
        worlds.remove(worldData)
        markDirty()
    }

    fun removeWorld(predicate: (PulsarWorldData) -> Boolean): Boolean {
        val world = worlds.find(predicate) ?: return false
        worlds.remove(world)
        markDirty()
        return true
    }

    fun removePortal(portalData: PulsarPortalData): Boolean {
        return portals.remove(portalData)
    }

    fun removePortal(predicate: (PulsarPortalData) -> Boolean): Boolean {
        val portal = portals.find(predicate) ?: return false
        portals.remove(portal)
        markDirty()
        return true
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: WrapperLookup): NbtCompound {
        val worldsNbtList = NbtList()
        val portalsNbtList = NbtList()

        for (entry in worlds) {
            val entryNbt = NbtCompound()
            entry.writeToNbt(entryNbt)
            worldsNbtList.add(entryNbt)
        }

        for (entry in portals) {
            val entryNbt = NbtCompound()
            entry.writeToNbt(entryNbt)
            portalsNbtList.add(entryNbt)
        }
        nbt.put("worlds", worldsNbtList)
        nbt.put("portals", portalsNbtList)
        return nbt
    }

    companion object {
        val PersistentStateTypeLoader = PersistentState.Type(
            { PulsarStorage() },
            { nbt: NbtCompound, registryLookup: WrapperLookup -> fromNbt(nbt, registryLookup) },
            null
        )

        @Suppress("UNUSED_PARAMETER")
        private fun fromNbt(nbt: NbtCompound, registryLookup: WrapperLookup): PulsarStorage {
            val pulsarStorage = PulsarStorage()
            val worldsNbtList = nbt.getList("worlds", 10) // 10 is the NbtCompound type
            val portalsNbt = nbt.getList("portals", 10)
            for (i in worldsNbtList.indices) {
                val entryNbt = worldsNbtList.getCompound(i)
                pulsarStorage.worlds.add(PulsarWorldData.fromNbt(entryNbt))
            }
            for (i in portalsNbt.indices) {
                val entryNbt = portalsNbt.getCompound(i)
                pulsarStorage.portals.add(PulsarPortalData.fromNbt(entryNbt))
            }
            return pulsarStorage
        }
    }
}