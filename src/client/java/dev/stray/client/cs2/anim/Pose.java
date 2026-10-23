package dev.stray.client.cs2.anim;

import dev.stray.client.cs2.s2.S2Model;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Poses CS2's first-person rig: the viewmodel arms skeleton plus the knife's own bones,
 * from one clip at a time. All matrices are in Source units, CS2 view space
 * (x forward, y left, z up) with the camera at the origin.
 */
public final class Pose {
	public final NmSkeleton viewmodel;
	public final NmSkeleton knifeSkeleton;
	public final S2Model knife;
	/** Model-space viewmodel bones. */
	public final Matrix4f[] vm;
	/** Per knife model bone: view-space transform for vertices at bind pose (anim * inverse bind). */
	public final Matrix4f[] skin;
	private final Matrix4f[] knifeInvBind;
	private final int[] knifeTrackForModelBone;
	public final int wpn;
	public final int handR;
	public final int lowerArmR;
	public final int upperArmR;
	/** Middle of the knife's handle in its bind pose (the handle runs from the mesh's lowest z up to the grip at z 0.5). */
	public final org.joml.Vector3f handle;

	public Pose(NmSkeleton viewmodel, NmSkeleton knifeSkeleton, S2Model knife) {
		this.viewmodel = viewmodel;
		this.knifeSkeleton = knifeSkeleton;
		this.knife = knife;
		vm = new Matrix4f[viewmodel.names.length];
		for (int i = 0; i < vm.length; i++) {
			vm[i] = new Matrix4f();
		}
		List<S2Model.Bone> bones = knife.bones;
		skin = new Matrix4f[Math.max(1, bones.size())];
		knifeInvBind = new Matrix4f[skin.length];
		knifeTrackForModelBone = new int[skin.length];
		Matrix4f[] bind = new Matrix4f[skin.length];
		for (int i = 0; i < skin.length; i++) {
			skin[i] = new Matrix4f();
			if (i >= bones.size()) {
				bind[i] = new Matrix4f();
				knifeInvBind[i] = new Matrix4f();
				knifeTrackForModelBone[i] = -1;
				continue;
			}
			S2Model.Bone b = bones.get(i);
			Matrix4f local = local(b.pos[0], b.pos[1], b.pos[2], b.quat[0], b.quat[1], b.quat[2], b.quat[3], 1f);
			bind[i] = b.parent >= 0 && b.parent < i ? new Matrix4f(bind[b.parent]).mul(local) : local;
			knifeInvBind[i] = new Matrix4f(bind[i]).invert();
			knifeTrackForModelBone[i] = knifeSkeleton == null ? -1 : knifeSkeleton.bone(b.name);
		}
		float minZ = 0f;
		for (S2Model.Part part : knife.parts) {
			for (int i = 0; i < part.vertexCount; i++) {
				minZ = Math.min(minZ, part.pos[i * 3 + 2]);
			}
		}
		handle = new org.joml.Vector3f(0f, 0f, (minZ + 0.5f) / 2f);
		wpn = viewmodel.bone("wpn");
		handR = viewmodel.bone("hand_R");
		lowerArmR = viewmodel.bone("arm_lower_R");
		upperArmR = viewmodel.bone("arm_upper_R");
	}

	static Matrix4f local(float x, float y, float z, float qx, float qy, float qz, float qw, float s) {
		return new Matrix4f().translation(x, y, z).rotate(new Quaternionf(qx, qy, qz, qw).normalize()).scale(s);
	}

	private static Matrix4f local(float[] t) {
		return local(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7]);
	}

	/** Reference pose (no clip). */
	public void reference() {
		apply(null, null);
	}

	/** Samples a clip at a time in seconds (clamped), blending between frames. */
	public void sample(NmClip clip, float seconds) {
		if (clip == null) {
			reference();
			return;
		}
		float t = clip.frames <= 1 ? 0f : Math.max(0f, Math.min(1f, seconds / clip.duration)) * (clip.frames - 1);
		int f0 = (int) Math.floor(t);
		int f1 = Math.min(clip.frames - 1, f0 + 1);
		float a = t - f0;
		float[][] vmPose = blend(clip.frame(f0), clip.frame(f1), a);
		float[][] knifePose = null;
		for (NmClip sec : clip.secondary) {
			if (knifeSkeleton != null && sec.skeleton.equalsIgnoreCase(knifeSkeletonId())) {
				knifePose = blend(sec.frame(f0), sec.frame(f1), a);
			}
		}
		if (knifePose == null && !clip.secondary.isEmpty()) {
			NmClip sec = clip.secondary.get(0);
			knifePose = blend(sec.frame(f0), sec.frame(f1), a);
		}
		apply(vmPose, knifePose);
	}

	private String id;

	private String knifeSkeletonId() {
		return id == null ? "" : id;
	}

	public Pose withKnifeSkeletonId(String id) {
		this.id = id;
		return this;
	}

	private static float[][] blend(float[][] a, float[][] b, float t) {
		float[][] out = new float[a.length][8];
		for (int i = 0; i < a.length; i++) {
			float[] p = a[i];
			float[] q = b[i];
			float[] o = out[i];
			for (int k = 0; k < 3; k++) {
				o[k] = p[k] + (q[k] - p[k]) * t;
			}
			Quaternionf qa = new Quaternionf(p[3], p[4], p[5], p[6]);
			Quaternionf qb = new Quaternionf(q[3], q[4], q[5], q[6]);
			qa.slerp(qb, t);
			o[3] = qa.x;
			o[4] = qa.y;
			o[5] = qa.z;
			o[6] = qa.w;
			o[7] = p[7] + (q[7] - p[7]) * t;
		}
		return out;
	}

	private void apply(float[][] vmPose, float[][] knifePose) {
		int n = viewmodel.names.length;
		for (int i = 0; i < n; i++) {
			float[] t = vmPose != null && i < vmPose.length ? vmPose[i] : viewmodel.reference[i];
			Matrix4f l = local(t);
			int p = viewmodel.parents[i];
			if (p >= 0 && p < i) {
				vm[i].set(vm[p]).mul(l);
			} else {
				vm[i].set(l);
			}
		}
		Matrix4f root = wpn >= 0 ? vm[wpn] : new Matrix4f();
		List<S2Model.Bone> bones = knife.bones;
		Matrix4f[] model = new Matrix4f[skin.length];
		for (int i = 0; i < skin.length; i++) {
			Matrix4f l;
			int track = knifeTrackForModelBone[i];
			if (knifePose != null && track >= 0 && track < knifePose.length) {
				l = local(knifePose[track]);
			} else if (i < bones.size()) {
				S2Model.Bone b = bones.get(i);
				l = local(b.pos[0], b.pos[1], b.pos[2], b.quat[0], b.quat[1], b.quat[2], b.quat[3], 1f);
			} else {
				l = new Matrix4f();
			}
			int p = i < bones.size() ? bones.get(i).parent : -1;
			model[i] = p >= 0 && p < i ? new Matrix4f(model[p]).mul(l) : new Matrix4f(root).mul(l);
			skin[i].set(model[i]).mul(knifeInvBind[i]);
		}
	}
}
