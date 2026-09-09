package dev.voidmark.client.visual;

/**
 * Extra flag on {@link net.minecraft.client.renderer.entity.state.EntityRenderState}
 * so fill ESP can run from submitted state without the live entity.
 */
public interface FillEspMarker {
	boolean voidmark$fillEsp();

	void voidmark$setFillEsp(boolean value);
}
