package dev.stray.client.mixin;

import dev.stray.client.config.EntityKind;
import dev.stray.client.visual.FillEspMarker;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements FillEspMarker {
	@Unique
	private boolean stray$fillEsp;
	@Unique
	private EntityKind stray$fillKind = EntityKind.PLAYER;

	@Override
	public boolean stray$fillEsp() {
		return this.stray$fillEsp;
	}

	@Override
	public void stray$setFillEsp(boolean value) {
		this.stray$fillEsp = value;
	}

	@Override
	public EntityKind stray$fillKind() {
		return this.stray$fillKind == null ? EntityKind.PLAYER : this.stray$fillKind;
	}

	@Override
	public void stray$setFillKind(EntityKind kind) {
		this.stray$fillKind = kind == null ? EntityKind.PLAYER : kind;
	}
}
