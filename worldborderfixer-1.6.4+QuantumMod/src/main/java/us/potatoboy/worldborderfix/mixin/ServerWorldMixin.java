package us.potatoboy.worldborderfix.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.potatoboy.worldborderfix.WorldBorderState;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "saveLevel", at = @At("HEAD"))
    private void saveBorder(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;

        // Create unique state ID for each world to avoid conflicts
        String stateId;
        boolean isQuantumWorld = world.getRegistryKey().getValue().getNamespace().equals("quantum");

        if (isQuantumWorld) {
            // For Quantum worlds, use a unique identifier per world
            stateId = "worldBorder_" + world.getRegistryKey().getValue().toString().replace(":", "_");
        } else {
            // For vanilla dimensions (Nether, End), use the original system
            stateId = "worldBorder";
        }

        // Get or create WorldBorder state for this specific world
        WorldBorderState worldBorderState = world.getPersistentStateManager().getOrCreate(
                new PersistentState.Type<>(WorldBorderState::new, WorldBorderState::fromNbt, null),
                stateId
        );

        // Save current WorldBorder settings to this world's unique state
        worldBorderState.fromBorder(world.getWorldBorder());

        if (isQuantumWorld) {
            System.out.println("[WorldBorderFix] Saved WorldBorder for Quantum world: " + world.getRegistryKey().getValue() +
                    " (Center: " + world.getWorldBorder().getCenterX() + "," + world.getWorldBorder().getCenterZ() +
                    ", Size: " + world.getWorldBorder().getSize() + ")");
        }
    }
}