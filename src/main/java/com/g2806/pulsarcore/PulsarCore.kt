package com.g2806.pulsarcore

import com.mojang.brigadier.CommandDispatcher
import com.g2806.pulsarcore.commands.GameRuleCommands
import com.g2806.pulsarcore.commands.WorldBorderCommands
import com.g2806.pulsarcore.commands.WorldCommands
import com.g2806.pulsarcore.events.EventHandlers
import com.g2806.pulsarcore.portal.PortalManager
import com.g2806.pulsarcore.world.WorldManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager.RegistrationEnvironment
import net.minecraft.server.command.ServerCommandSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PulsarCore : ModInitializer {

    companion object {
        const val MOD_ID = "pulsarcore"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }

    // =========================================================================
    // MAIN INITIALIZATION
    // =========================================================================

    override fun onInitialize() {
        LOGGER.info("===========================================")
        LOGGER.info("[PulsarCore] Initializing...")
        LOGGER.info("[PulsarCore] Features:")
        LOGGER.info("[PulsarCore] ✓ Multi-world management")
        LOGGER.info("[PulsarCore] ✓ Portal system")
        LOGGER.info("[PulsarCore] ✓ Per-world borders")
        LOGGER.info("[PulsarCore] ✓ Vanilla dimensions support")
        LOGGER.info("[PulsarCore] ✓ Quantum Mod compatibility")
        LOGGER.info("[PulsarCore] ✓ Dynamic world detection")
        LOGGER.info("[PulsarCore] ✓ Per-world persistence")
        
        registerCommands()

        ServerLifecycleEvents.SERVER_STARTED.register(ServerStarted { server: MinecraftServer ->
            WorldManager.loadWorlds(server)
            PortalManager.loadPortals(server)
        })

        EventHandlers.registerEvents()
        
        LOGGER.info("[PulsarCore] Ready!")
        LOGGER.info("===========================================")
    }

    // =========================================================================
    // COMMAND REGISTRATION
    // =========================================================================

    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register(CommandRegistrationCallback { dispatcher: CommandDispatcher<ServerCommandSource>,
                                                                                 registryAccess: CommandRegistryAccess,
                                                                                 environment: RegistrationEnvironment ->
            if (!environment.integrated && !environment.dedicated) return@CommandRegistrationCallback

            // Register all command modules
            WorldCommands.register(dispatcher)
            GameRuleCommands.register(dispatcher)
            WorldBorderCommands.register(dispatcher)
            registerHelpCommands(dispatcher)
        })
    }

    private fun registerHelpCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // Main /pc command - shows basic commands
        dispatcher.register(net.minecraft.server.command.CommandManager.literal("pc")
            .requires { commandSource: ServerCommandSource -> commandSource.hasPermissionLevel(4) }
            .executes { context ->
                if (context.source == null) return@executes 0

                val source = context.source
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6═══════════════ §ePulsarCore Commands §6═══════════════"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6▌ §eWorld Management:"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc createworld <name> [difficulty] [dimension] [seed] §8- Create new world"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc deleteworld <world> §8- Delete existing world"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc tp <world> §8- Teleport to world"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6▌ §eWorld Configuration:"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc setSpawn [radius] §8- Set world spawn point"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc setdestination <world> §8- Set teleport sign destination"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6▌ §ePortal System:"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc createportal <block> <item> <destination> §8- Create portal"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc deleteportal <block> §8- Delete portal"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6▌ §eWorldBorder:"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pc worldborder §8- WorldBorder configuration"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6▌ §eGameRules:"))
                source.sendMessage(net.minecraft.text.Text.literal("§7  /pcg §8- Show all GameRule commands"))
                source.sendMessage(net.minecraft.text.Text.literal(""))
                source.sendMessage(net.minecraft.text.Text.literal("§6═══════════════════════════════════════════════════"))

                return@executes 1
            }
        )
    }
}