package dev.stray.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
	@Invoker("setPosition")
	void stray$setPosition(Vec3 position);

	@Invoker("setRotation")
	void stray$setRotation(float yRot, float xRot);

	@Invoker("setupPerspective")
	void stray$setupPerspective(float zNear, float zFar, float fov, float width, float height);

	@Accessor("cullFrustum")
	Frustum stray$cullFrustum();

	@Accessor("cullFrustum")
	void stray$cullFrustum(Frustum frustum);

	@Accessor("depthFar")
	float stray$depthFar();
}
