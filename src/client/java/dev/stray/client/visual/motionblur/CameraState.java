package dev.stray.client.visual.motionblur;

import org.joml.Matrix4f;

public final class CameraState {
	private final Matrix4f mvInverse = new Matrix4f();
	private final Matrix4f projInverse = new Matrix4f();
	private final Matrix4f prevModelView = new Matrix4f();
	private final Matrix4f prevProjection = new Matrix4f();
	private float dx;
	private float dy;
	private float dz;

	public void setFrame(
		Matrix4f modelView,
		Matrix4f previousModelView,
		Matrix4f projection,
		Matrix4f previousProjection,
		float dx,
		float dy,
		float dz
	) {
		modelView.invert(mvInverse);
		projection.invert(projInverse);
		prevModelView.set(previousModelView);
		prevProjection.set(previousProjection);
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
	}

	public Matrix4f getMvInverse() {
		return mvInverse;
	}

	public Matrix4f getProjInverse() {
		return projInverse;
	}

	public Matrix4f getPrevModelView() {
		return prevModelView;
	}

	public Matrix4f getPrevProjection() {
		return prevProjection;
	}

	public float getDx() {
		return dx;
	}

	public float getDy() {
		return dy;
	}

	public float getDz() {
		return dz;
	}
}
