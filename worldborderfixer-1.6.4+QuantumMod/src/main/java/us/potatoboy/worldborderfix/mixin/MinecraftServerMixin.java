package us.potatoboy.worldborderfix.mixin;

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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.potatoboy.worldborderfix.PerWorldBorderListener;
import us.potatoboy.worldborderfix.WorldBorderState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Timer;
import java.util.TimerTask;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Shadow
	@Final
	private Map<RegistryKey<World>, ServerWorld> worlds;

	// Track processed worlds to avoid reprocessing
	private static final Map<String, Boolean> processedWorlds = new ConcurrentHashMap<>();
	private static Timer worldMonitor;

	@Inject(method = "createWorlds", at = @At(value = "TAIL"))
	private void loadOtherBorder(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci) {
		// Process existing worlds
		processAllWorlds();

		// Start monitoring for new Quantum worlds every 2 seconds
		if (worldMonitor == null) {
			worldMonitor = new Timer("QuantumWorldMonitor");
			worldMonitor.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					processAllWorlds();
				}
			}, 2000, 2000); // Check every 2 seconds
		}
	}

	private void processAllWorlds() {
		worlds.forEach((registryKey, world) -> {
			String worldId = registryKey.getValue().toString();

			// Skip if already processed
			if (processedWorlds.containsKey(worldId)) {
				return;
			}

			WorldBorder worldBorder = world.getWorldBorder();

			// Apply to all non-Overworld dimensions (including Quantum worlds)
			if (registryKey.getValue() != DimensionOptions.OVERWORLD.getValue()) {

				// Create unique state ID for each world to avoid conflicts
				String stateId;
				boolean isQuantumWorld = registryKey.getValue().getNamespace().equals("quantum");

				if (isQuantumWorld) {
					// For Quantum worlds, use a unique identifier per world
					stateId = "worldBorder_" + registryKey.getValue().toString().replace(":", "_");
					System.out.println("[WorldBorderFix] Detecting new Quantum world: " + registryKey.getValue());
				} else {
					// For vanilla dimensions (Nether, End), use the original system
					stateId = "worldBorder";
				}

				// Load WorldBorder state for this specific world
				WorldBorderState worldBorderState = world.getPersistentStateManager().getOrCreate(
						new PersistentState.Type<>(WorldBorderState::new, WorldBorderState::fromNbt, null),
						stateId
				);

				// For new Quantum worlds, set reasonable defaults if no data exists
				if (isQuantumWorld && isDefaultWorldBorderState(worldBorderState)) {
					// Set smaller default for Quantum worlds
					worldBorder.setCenter(0.0, 0.0);
					worldBorder.setSize(1000.0);
					worldBorder.setDamagePerBlock(0.2);
					worldBorder.setSafeZone(5.0);
					worldBorder.setWarningBlocks(5);
					worldBorder.setWarningTime(15);

					// Save the defaults immediately
					worldBorderState.fromBorder(worldBorder);

					System.out.println("[WorldBorderFix] Set default WorldBorder for new Quantum world: " + registryKey.getValue());
				} else {
					// Apply the saved WorldBorder configuration
					worldBorder.setCenter(worldBorderState.getCenterX(), worldBorderState.getCenterZ());
					worldBorder.setSize(worldBorderState.getSize());
					worldBorder.setSafeZone(worldBorderState.getBuffer());
					worldBorder.setDamagePerBlock(worldBorderState.getDamagePerBlock());
					worldBorder.setWarningBlocks(worldBorderState.getWarningBlocks());
					worldBorder.setWarningTime(worldBorderState.getWarningTime());

					if (isQuantumWorld) {
						System.out.println("[WorldBorderFix] Loaded saved WorldBorder for Quantum world: " + registryKey.getValue() +
								" (Center: " + worldBorderState.getCenterX() + "," + worldBorderState.getCenterZ() +
								", Size: " + worldBorderState.getSize() + ")");
					}
				}
			}

			// Add listener to ALL worlds (vanilla + Quantum) - but only once
			if (!hasQuantumListener(worldBorder)) {
				worldBorder.addListener(new PerWorldBorderListener(world));
			}

			// Mark as processed
			processedWorlds.put(worldId, true);
		});
	}

	// Check if WorldBorder state contains default values (indicating new world)
	private boolean isDefaultWorldBorderState(WorldBorderState state) {
		return state.getSize() == WorldBorder.DEFAULT_BORDER.getSize() &&
				state.getCenterX() == WorldBorder.DEFAULT_BORDER.getCenterX() &&
				state.getCenterZ() == WorldBorder.DEFAULT_BORDER.getCenterZ();
	}

	// Check if world already has our listener to avoid duplicates
	private boolean hasQuantumListener(WorldBorder border) {
		// This is a simple check - in practice, we could use reflection or other methods
		// For now, we'll rely on the processedWorlds map to prevent duplicates
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
		// Prevent default listener registration - we handle it manually above
	}

	@Redirect(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;setMainWorld(Lnet/minecraft/server/world/ServerWorld;)V"))
	private void setBorderListeners(PlayerManager playerManager, ServerWorld world) {
		// Prevent default main world setup - we handle all worlds equally
	}
}