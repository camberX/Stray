package dev.stray.client.mixin;

import dev.stray.client.visual.FillEspMarker;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements FillEspMarker {
	@Unique
	private boolean stray$fillEsp;

	@Override
	public boolean stray$fillEsp() {
		return this.stray$fillEsp;
	}

	@Override
	public void stray$setFillEsp(boolean value) {
		this.stray$fillEsp = value;
	}
}
