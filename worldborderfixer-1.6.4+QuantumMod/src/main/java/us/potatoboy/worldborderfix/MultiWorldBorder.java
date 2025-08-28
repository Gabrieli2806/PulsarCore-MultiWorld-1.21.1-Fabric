package us.potatoboy.worldborderfix;

import net.fabricmc.api.ModInitializer;

public class MultiWorldBorder implements ModInitializer {
	@Override
	public void onInitialize() {
		System.out.println("===========================================");
		System.out.println("[WorldBorderFix] Initializing...");
		System.out.println("[WorldBorderFix] Features:");
		System.out.println("[WorldBorderFix] ✓ Vanilla dimensions support");
		System.out.println("[WorldBorderFix] ✓ Quantum Mod compatibility");
		System.out.println("[WorldBorderFix] ✓ Dynamic world detection");
		System.out.println("[WorldBorderFix] ✓ Per-world persistence");
		System.out.println("[WorldBorderFix] Ready!");
		System.out.println("===========================================");
	}
}