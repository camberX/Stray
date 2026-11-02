package dev.stray.client.mixin;

import dev.stray.client.visual.ChestHolder;
import dev.stray.client.visual.ShopWings;
import dev.stray.client.visual.WingsHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements WingsHolder, ChestHolder {
	@Unique
	private boolean stray$chest;

	@Unique
	private ShopWings.Wings stray$wings;

	@Override
	public ShopWings.Wings stray$wings() {
		return this.stray$wings;
	}

	@Override
	public void stray$setWings(ShopWings.Wings wings) {
		this.stray$wings = wings;
	}

	@Override
	public boolean stray$chest() {
		return this.stray$chest;
	}

	@Override
	public void stray$setChest(boolean chest) {
		this.stray$chest = chest;
	}
}
