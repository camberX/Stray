package dev.stray.client.visual.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.stray.Stray;
import dev.stray.client.config.StrayConfig;
import dev.stray.client.mixin.PostChainAccessor;
import dev.stray.client.mixin.PostPassAccessor;
import dev.stray.client.mixin.ShaderManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MotionBlurShaders {
	private static final FrameTimer FRAME_TIMER = new FrameTimer();
	private static final CameraState CAMERA = new CameraState();
	private static final BlurStrengthCalculator STRENGTH = new BlurStrengthCalculator();
	private static final int UBO_SIZE = 304;
	private static final ManagedUniformBuffer PRE_UBO = new ManagedUniformBuffer("PreEntityBlurUniforms", UBO_SIZE);
	private static final ManagedUniformBuffer F5_UBO = new ManagedUniformBuffer("PreEntityBlurUniforms", UBO_SIZE);
	private static final ManagedUniformBuffer POST_UBO = new ManagedUniformBuffer("PostRenderBlurUniforms", UBO_SIZE);

	private static GraphicsResourceAllocator frameAllocator;
	private static boolean deferredTemporalApplied;
	private static boolean cameraStill;
	private static final Identifier PRE_ID = Stray.id("velocity_pre");
	private static final Identifier F5_ID = Stray.id("velocity_f5");
	private static final Identifier POST_ID = Stray.id("velocity_post");
	private static PostChain cachedPre;
	private static PostChain cachedF5;
	private static PostChain cachedPost;
	private static final Set<String> LOAD_ERRORS = new HashSet<>();

	private enum BlurPass {
		NORMAL_PRE,
		SPECIAL_F5,
		NORMAL_POST
	}

	private MotionBlurShaders() {
	}

	public static boolean active() {
		StrayConfig config = StrayConfig.get();
		return config.motionBlurEnabled && config.motionBlurStrength != 0f;
	}

	public static void captureAllocator(GraphicsResourceAllocator allocator) {
		frameAllocator = allocator;
	}

	public static void clearFrameAllocator() {
		frameAllocator = null;
	}

	public static void beginFrame() {
		FRAME_TIMER.beginFrame();
		deferredTemporalApplied = false;
	}

	public static void invalidate() {
		PRE_UBO.reset();
		F5_UBO.reset();
		POST_UBO.reset();
		FrameBlendingManager.invalidate();
	}

	public static void setFrameMotionBlur(
		Matrix4f modelView,
		Matrix4f prevModelView,
		Matrix4f projection,
		Matrix4f prevProjection,
		float dx,
		float dy,
		float dz
	) {
		CAMERA.setFrame(modelView, prevModelView, projection, prevProjection, dx, dy, dz);
		cameraStill = Math.abs(dx) < 1.0E-5f
			&& Math.abs(dy) < 1.0E-5f
			&& Math.abs(dz) < 1.0E-5f
			&& modelView.equals(prevModelView, 1.0E-6f)
			&& projection.equals(prevProjection, 1.0E-6f);
	}

	public static void applyPreEntityBlur() {
		if (active()) {
			applyBlur(BlurPass.NORMAL_PRE, true);
		}
	}

	public static void applyF5EntityRideBlur() {
		if (active()) {
			applyBlur(BlurPass.SPECIAL_F5, true);
		}
	}

	public static void applyPostRenderVelocityOnly() {
		if (active()) {
			applyBlur(BlurPass.NORMAL_POST, false);
		}
	}

	public static void applyDeferredTemporalBlur() {
		if (deferredTemporalApplied || frameAllocator == null || !active()) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		switch (config.motionBlurAlgorithm()) {
			case FRAME_BLENDING, HYBRID_BLENDING -> {
				applyFrameBlending();
				deferredTemporalApplied = true;
			}
			case ACCUMULATION_MAX -> {
				FrameBlendingManager.applyAccumulationMax(frameAllocator, config.motionBlurStrength);
				deferredTemporalApplied = true;
			}
			case ACCUMULATION_MIX -> {
				FrameBlendingManager.applyAccumulationMix(frameAllocator, config.motionBlurStrength);
				deferredTemporalApplied = true;
			}
			default -> {
			}
		}
	}

	private static void applyBlur(BlurPass pass, boolean includeTemporal) {
		if (frameAllocator == null) {
			return;
		}
		StrayConfig config = StrayConfig.get();
		Minecraft client = Minecraft.getInstance();
		if (includeTemporal) {
			if (config.motionBlurAlgorithm() == StrayConfig.MotionBlurAlgorithm.FRAME_BLENDING
				|| config.motionBlurAlgorithm() == StrayConfig.MotionBlurAlgorithm.ACCUMULATION_MAX
				|| config.motionBlurAlgorithm() == StrayConfig.MotionBlurAlgorithm.ACCUMULATION_MIX) {
				return;
			}
		} else if (!config.motionBlurUsesVelocity()) {
			return;
		}
		// Velocity blur reprojects against last frame's camera. A still camera
		// gives zero-length samples, so the fullscreen pass would only copy pixels.
		if (cameraStill) {
			return;
		}

		BlurStrengthCalculator.Result blur = velocityBlur(config);
		float viewW = client.getMainRenderTarget().width;
		float viewH = client.getMainRenderTarget().height;
		int algo = config.motionBlurAlgorithm().ordinal();
		switch (pass) {
			case NORMAL_PRE -> {
				PostChain chain = cachedPre = loadProcessor(client, PRE_ID, "velocity_pre");
				if (chain != null) {
					writeAndRun(chain, "PreEntityBlurUniforms", PRE_UBO, blur.strength(), viewW, viewH, algo, blur.sampleAmount(), client);
				}
			}
			case SPECIAL_F5 -> {
				PostChain chain = cachedF5 = loadProcessor(client, F5_ID, "velocity_f5");
				if (chain != null) {
					writeAndRun(chain, "PreEntityBlurUniforms", F5_UBO, blur.strength(), viewW, viewH, algo, blur.sampleAmount(), client);
				}
			}
			case NORMAL_POST -> {
				PostChain chain = cachedPost = loadProcessor(client, POST_ID, "velocity_post");
				if (chain != null) {
					writeAndRun(chain, "PostRenderBlurUniforms", POST_UBO, blur.strength(), viewW, viewH, algo, blur.sampleAmount(), client);
				}
				if (includeTemporal && config.motionBlurAlgorithm() == StrayConfig.MotionBlurAlgorithm.HYBRID_BLENDING) {
					applyFrameBlending();
				}
			}
		}
	}

	private static void applyFrameBlending() {
		if (frameAllocator == null) {
			return;
		}
		FrameBlendingManager.applyFrameBlending(
			frameAllocator,
			FRAME_TIMER.getFps(),
			FRAME_TIMER.getRefreshRate(),
			StrayConfig.get().motionBlurStrength
		);
	}

	private static BlurStrengthCalculator.Result velocityBlur(StrayConfig config) {
		float fps = FRAME_TIMER.getFps();
		int refresh = FRAME_TIMER.getRefreshRate();
		if (config.motionBlurAlgorithm() == StrayConfig.MotionBlurAlgorithm.HYBRID_BLENDING) {
			float filler = FrameBlendingManager.getHybridVelocityStrength(fps, refresh, config.motionBlurStrength);
			int samples = Math.max(100, Math.round(100.0f * filler));
			return new BlurStrengthCalculator.Result(filler, samples);
		}
		return STRENGTH.calculate(
			config.motionBlurStrength,
			fps,
			refresh,
			config.motionBlurRefreshScale && config.motionBlurAllowsRefreshScale()
		);
	}

	static PostChain loadProcessor(Minecraft client, String shaderName) {
		try {
			net.minecraft.client.renderer.ShaderManager.CompilationCache cache =
				((ShaderManagerAccessor) client.getShaderManager()).getCompilationCache();
			if (cache == null) {
				return null;
			}
			PostChain chain = cache.getOrLoadPostChain(Stray.id(shaderName), LevelTargetBundle.MAIN_TARGETS);
			LOAD_ERRORS.remove(shaderName);
			return chain;
		} catch (Exception e) {
			if (LOAD_ERRORS.add(shaderName)) {
				Stray.LOGGER.warn("Failed to load motion blur shader {}", shaderName, e);
			}
			return null;
		}
	}

	static PostChain loadProcessor(Minecraft client, Identifier id, String shaderName) {
		try {
			net.minecraft.client.renderer.ShaderManager.CompilationCache cache =
				((ShaderManagerAccessor) client.getShaderManager()).getCompilationCache();
			if (cache == null) {
				return null;
			}
			PostChain chain = cache.getOrLoadPostChain(id, LevelTargetBundle.MAIN_TARGETS);
			LOAD_ERRORS.remove(shaderName);
			return chain;
		} catch (Exception e) {
			if (LOAD_ERRORS.add(shaderName)) {
				Stray.LOGGER.warn("Failed to load motion blur shader {}", shaderName, e);
			}
			return null;
		}
	}

	private static void writeAndRun(
		PostChain processor,
		String uboKey,
		ManagedUniformBuffer managed,
		float blendFactor,
		float viewW,
		float viewH,
		int blurAlgorithm,
		int sampleAmount,
		Minecraft client
	) {
		List<PostPass> passes = ((PostChainAccessor) processor).getPasses();
		if (passes.isEmpty()) {
			return;
		}
		Map<String, GpuBuffer> uniforms = ((PostPassAccessor) passes.getFirst()).getCustomUniforms();
		if (!uniforms.containsKey(uboKey)) {
			return;
		}
		GpuBuffer ubo = managed.put(processor, uniforms, uboKey);
		try {
			GpuBufferUtil.write(ubo, UBO_SIZE, builder -> {
				builder.putMat4f(CAMERA.getMvInverse());
				builder.putMat4f(CAMERA.getProjInverse());
				builder.putMat4f(CAMERA.getPrevModelView());
				builder.putMat4f(CAMERA.getPrevProjection());
				builder.putVec3(CAMERA.getDx(), CAMERA.getDy(), CAMERA.getDz());
				builder.putVec2(viewW, viewH);
				builder.putFloat(blendFactor);
				builder.putInt(sampleAmount);
				builder.putInt(blurAlgorithm);
				builder.putInt(1);
			});
			processor.process(client.getMainRenderTarget(), frameAllocator);
		} catch (RuntimeException e) {
			if (managed.resetIfClosed(e)) {
				return;
			}
			Stray.LOGGER.warn("Motion blur skipped a frame", e);
		}
	}
}
