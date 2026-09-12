package dev.stray.client.visual;

import dev.stray.client.config.EntityKind;

/**
 * Extra flag on {@link net.minecraft.client.renderer.entity.state.EntityRenderState}
 * so fill ESP can run from submitted state without the live entity.
 */
public interface FillEspMarker {
	boolean stray$fillEsp();

	void stray$setFillEsp(boolean value);

	EntityKind stray$fillKind();

	void stray$setFillKind(EntityKind kind);
}
