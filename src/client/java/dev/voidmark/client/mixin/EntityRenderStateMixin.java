package dev.voidmark.client.mixin;

import dev.voidmark.client.visual.FillEspMarker;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements FillEspMarker {
	@Unique
	private boolean voidmark$fillEsp;

	@Override
	public boolean voidmark$fillEsp() {
		return this.voidmark$fillEsp;
	}

	@Override
	public void voidmark$setFillEsp(boolean value) {
		this.voidmark$fillEsp = value;
	}
}
