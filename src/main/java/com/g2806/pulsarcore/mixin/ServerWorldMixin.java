package com.g2806.pulsarcore.mixin;

import com.g2806.pulsarcore.worldborder.BorderWithWorld;
import com.g2806.pulsarcore.worldborder.PerWorldBorderListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setupPerWorldBorder(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        WorldBorder border = world.getWorldBorder();
        
        // Set up the border-world association
        if (border instanceof BorderWithWorld) {
            ((BorderWithWorld) border).setWorld(world);
        }
        
        // Add per-world border listener
        border.addListener(new PerWorldBorderListener(world));
    }
}