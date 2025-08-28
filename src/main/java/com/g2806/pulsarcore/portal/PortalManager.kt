package com.g2806.pulsarcore.portal

import com.g2806.pulsarcore.PulsarCore
import com.g2806.pulsarcore.data.PulsarPortalData
import com.g2806.pulsarcore.storage.StorageManager
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier

object PortalManager {

    fun loadPortals(server: MinecraftServer) {
        val state = StorageManager.getPulsarState(server)

        for (portal in state.getPortals()) {
            val itemPortal = Registries.ITEM.get(portal.portalIgniteItemId)
            PulsarCore.LOGGER.info("Found portal '{}', loading it.", portal.destinationId)
            CustomPortalBuilder.beginPortal()
                .frameBlock(portal.portalBlockId)
                .lightWithItem(itemPortal)
                .destDimID(portal.destinationId)
                .tintColor(portal.portalColor)
                .registerPortal()
        }
    }

    fun createPortal(
        server: MinecraftServer,
        portalBlockId: Identifier,
        portalItemId: Identifier,
        destinationId: Identifier
    ): Boolean {
        val pulsarStorage = StorageManager.getPulsarState(server)

        if (pulsarStorage.getPortal { it.portalBlockId == portalBlockId } != null) {
            return false // Portal already exists
        }

        val portalBlock = Registries.BLOCK.get(portalBlockId)
        val portalItem = Registries.ITEM.get(portalItemId)

        val portalLink = CustomPortalBuilder.beginPortal()
            .frameBlock(portalBlock)
            .lightWithItem(portalItem)
            .destDimID(destinationId)
            .tintColor(240, 142, 25)
            .registerPortal()

        pulsarStorage.addPortal(PulsarPortalData(portalLink.dimID, portalLink.block, portalItemId, portalLink.colorID))
        return true
    }

    fun deletePortal(server: MinecraftServer, portalBlockId: Identifier): Boolean {
        val pulsarStorage = StorageManager.getPulsarState(server)
        val portal = pulsarStorage.getPortal { it.portalBlockId == portalBlockId }

        return if (portal != null) {
            pulsarStorage.removePortal(portal)
        } else {
            false
        }
    }
}