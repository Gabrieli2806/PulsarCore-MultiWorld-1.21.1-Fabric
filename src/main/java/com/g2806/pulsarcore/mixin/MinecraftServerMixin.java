package com.g2806.pulsarcore.mixin;

import com.g2806.pulsarcore.PulsarCore;
import com.g2806.pulsarcore.storage.StorageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.g2806.pulsarcore.worldborder.PerWorldBorderListener;
import com.g2806.pulsarcore.worldborder.PulsarWorldBorderState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Shadow
	@Final
	private Map<RegistryKey<World>, ServerWorld> worlds;

	private static final Map<String, Boolean> processedWorlds = new ConcurrentHashMap<>();
	private static Timer worldMonitor;

	@Inject(method = "createWorlds", at = @At(value = "TAIL"))
	private void loadOtherBorder(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci) {
		processAllWorlds();

		if (worldMonitor == null) {
			worldMonitor = new Timer("PulsarWorldMonitor");
			worldMonitor.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					processAllWorlds();
				}
			}, 2000, 2000);
		}
	}

	private void processAllWorlds() {
		worlds.forEach((registryKey, world) -> {
			String worldId = registryKey.getValue().toString();

			if (processedWorlds.containsKey(worldId)) {
				return;
			}

			WorldBorder worldBorder = world.getWorldBorder();
			boolean isQuantumWorld = registryKey.getValue().getNamespace().equals("quantum");
			boolean isPulsarWorld = registryKey.getValue().getNamespace().equals("pulsar");
			
			// Check if this world is managed by PulsarCore
			boolean isPulsarManagedWorld = false;
			try {
				MinecraftServer server = (MinecraftServer) (Object) this;
				var pulsarStorage = StorageManager.INSTANCE.getPulsarState(server);
				isPulsarManagedWorld = pulsarStorage.getWorlds().stream()
					.anyMatch(worldData -> worldData.getWorldId().equals(registryKey.getValue()));
			} catch (Exception e) {
				// Ignore error, continue without PulsarCore integration
			}
			
			// Define standard dimension identifiers
			Identifier overworldId = DimensionOptions.OVERWORLD.getValue();
			Identifier netherId = Identifier.of("minecraft", "the_nether");
			Identifier endId = Identifier.of("minecraft", "the_end");
			
			boolean isCustomWorld = !registryKey.getValue().equals(overworldId) && 
									!registryKey.getValue().equals(netherId) && 
									!registryKey.getValue().equals(endId);

			// Apply per-dimension worldborder for ALL non-Overworld dimensions
			if (!registryKey.getValue().equals(overworldId)) {
				// Create unique state ID for EVERY dimension (including Nether, End, Quantum, PulsarCore, etc.)
				String stateId = "pulsarcore_worldborder_" + registryKey.getValue().toString().replace(":", "_");
				
				if (isQuantumWorld) {
					System.out.println("[PulsarCore] Configuring WorldBorder for Quantum world: " + registryKey.getValue());
				} else if (isPulsarWorld || isPulsarManagedWorld) {
					System.out.println("[PulsarCore] Configuring WorldBorder for PulsarCore-managed world: " + registryKey.getValue());
				} else if (isCustomWorld) {
					System.out.println("[PulsarCore] Configuring WorldBorder for custom world: " + registryKey.getValue());
				} else {
					System.out.println("[PulsarCore] Configuring WorldBorder for vanilla dimension: " + registryKey.getValue());
				}

				PulsarWorldBorderState worldBorderState = world.getPersistentStateManager().getOrCreate(
						PulsarWorldBorderState.Companion.getTYPE(),
						stateId
				);

				// Apply appropriate defaults or load saved state
				if (isDefaultWorldBorderState(worldBorderState)) {
					// Set dimension-specific defaults
					double defaultSize;
					if (isQuantumWorld) {
						defaultSize = 1000.0; // Smaller for Quantum worlds
					} else if (isPulsarWorld || isPulsarManagedWorld) {
						defaultSize = 2000.0; // Medium for PulsarCore-managed worlds
					} else if (registryKey.getValue().equals(netherId)) {
						defaultSize = 1000.0; // Smaller for Nether
					} else if (registryKey.getValue().equals(endId)) {
						defaultSize = 1000.0; // Smaller for End
					} else {
						defaultSize = 1500.0; // Default for other custom dimensions
					}

					worldBorder.setCenter(0.0, 0.0);
					worldBorder.setSize(defaultSize);
					worldBorder.setDamagePerBlock(0.2);
					worldBorder.setSafeZone(5.0);
					worldBorder.setWarningBlocks(5);
					worldBorder.setWarningTime(15);

					// Save the new defaults
					worldBorderState.fromBorder(worldBorder);

					System.out.println("[PulsarCore] Set default WorldBorder for dimension " + registryKey.getValue() + 
						" (Size: " + defaultSize + ")");
				} else {
					// Apply saved configuration
					worldBorderState.applyToBorder(worldBorder);

					System.out.println("[PulsarCore] Loaded saved WorldBorder for dimension " + registryKey.getValue() +
						" (Center: " + worldBorderState.getCenterX() + "," + worldBorderState.getCenterZ() +
						", Size: " + worldBorderState.getSize() + ")");
				}

				// The PerWorldBorderListener is already set up by ServerWorldMixin
				// No need to duplicate the listener setup here
			}

			processedWorlds.put(worldId, true);
		});
	}

	private boolean isDefaultWorldBorderState(PulsarWorldBorderState state) {
		return state.getSize() == WorldBorder.DEFAULT_BORDER.getSize() &&
				state.getCenterX() == WorldBorder.DEFAULT_BORDER.getCenterX() &&
				state.getCenterZ() == WorldBorder.DEFAULT_BORDER.getCenterZ();
	}

	private boolean hasQuantumListener(WorldBorder border) {
		return false;
	}

	@Inject(method = "shutdown", at = @At("HEAD"))
	private void cleanup(CallbackInfo ci) {
		if (worldMonitor != null) {
			worldMonitor.cancel();
			worldMonitor = null;
		}
		processedWorlds.clear();
	}

	@Redirect(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/border/WorldBorder;addListener(Lnet/minecraft/world/border/WorldBorderListener;)V"))
	private void addListener(WorldBorder worldBorder, WorldBorderListener listener) {
	}

	@Redirect(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;setMainWorld(Lnet/minecraft/server/world/ServerWorld;)V"))
	private void setBorderListeners(PlayerManager playerManager, ServerWorld world) {
	}
}