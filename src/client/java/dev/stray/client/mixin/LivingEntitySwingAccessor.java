package dev.stray.client.mixin;

import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * The arm swing's own state, so Auto Leap can play the swing on your screen without going
 * through {@code swing(...)}, which other mods (ViaFabricPlus) may turn into a packet.
 * Full edition only.
 */
@Mixin(LivingEntity.class)
public interface LivingEntitySwingAccessor {
	//? if >=26.3 {
	@Accessor("swingState")
	LivingEntity.SwingState stray$swingState();

	@Invoker("getModifiedSwingDuration")
	int stray$swingDuration(SwingAnimation animation);
	//?}
}
