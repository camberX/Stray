package dev.stray.client.visual.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.stray.Stray;
import dev.stray.client.mixin.PostChainAccessor;
import dev.stray.client.mixin.PostPassAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class FrameBlendingManager {
	private static final int FRAME_BLEND_UBO_SIZE = 64;
	private static final int ACCUM_UBO_SIZE = 16;
	private static final int MAX_HISTORY = 24;
	private static final int FRAME_BLEND_SAMPLE_LIMIT = 12;
	private static final int UBO_RING_SIZE = 3;
	private static final String MAIN_SAMPLER = "Main";
	private static final String PREV_SAMPLER = "Prev";
	private static final String FRAME_BLEND_UBO = "FrameBlendParamsUniforms";
	private static final String ACCUM_UBO = "AccumulationUniforms";
	private static final Identifier FRAME_BLENDING_ID = Stray.id("frame_blending");
	private static final Identifier ACCUMULATION_MAX_ID = Stray.id("accumulation_max");
	private static final Identifier ACCUMULATION_MIX_ID = Stray.id("accumulation_mix");
	private static final String[] SAMPLE_NAMES = new String[MAX_HISTORY];

	static {
		for (int i = 0; i < MAX_HISTORY; i++) {
			SAMPLE_NAMES[i] = "Sample" + i;
		}
	}

	private static PostChain cachedCombineChain;
	private static final ManagedUniformBuffer.Ring COMBINE_UBO = new ManagedUniformBuffer.Ring(FRAME_BLEND_UBO, FRAME_BLEND_UBO_SIZE, UBO_RING_SIZE);
	private static final RenderTarget[] HISTORY = new RenderTarget[MAX_HISTORY];
	private static final MutableTextureInput[] HISTORY_INPUTS = new MutableTextureInput[MAX_HISTORY];
	private static final double[] HISTORY_TIMES = new double[MAX_HISTORY];
	private static final int[] WEIGHTED_INDICES = new int[MAX_HISTORY];
	private static final float[] WEIGHTED_WEIGHTS = new float[MAX_HISTORY];
	private static final int[] COMPACT_INDICES = new int[MAX_HISTORY];
	private static final float[] COMPACT_WEIGHTS = new float[MAX_HISTORY];
	private static int historyWriteIndex;
	private static int historyFilled;
	private static float smoothedFps;
	private static PostChain cachedAccumMax;
	private static PostChain cachedAccumMix;
	private static final ManagedUniformBuffer ACCUM_UBO_BUF = new ManagedUniformBuffer(ACCUM_UBO, ACCUM_UBO_SIZE);
	private static RenderTarget accumRead;
	private static RenderTarget accumWrite;
	private static MutableTextureInput injectedMain;
	private static MutableTextureInput injectedPrev;
	private static boolean accumHasPrevious;
	private static int targetW;
	private static int targetH;

	private FrameBlendingManager() {
	}

	public static void applyFrameBlending(GraphicsResourceAllocator allocator, float fps, int refreshRate, float strength) {
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		updateSmoothedFps(fps);
		if (refreshRate <= 0 || strength <= 0.0f) {
			historyWriteIndex = 0;
			historyFilled = 0;
			return;
		}
		ensureTargets(main.width, main.height);
		double now = System.nanoTime() * 1.0E-9;
		pushHistory(main, now);
		int sampleCount = buildWeightedSampleList(now, refreshRate, smoothedFps, strength);
		if (sampleCount <= 1) {
			return;
		}
		PostChain combine = MotionBlurShaders.loadProcessor(client, FRAME_BLENDING_ID, "frame_blending");
		if (combine == null) {
			return;
		}
		if (combine != cachedCombineChain) {
			cachedCombineChain = combine;
		}
		PostPass pass = firstPass(combine);
		if (pass == null) {
			return;
		}
		Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).getCustomUniforms();
		if (!uniforms.containsKey(FRAME_BLEND_UBO)) {
			return;
		}
		GpuBuffer ubo = COMBINE_UBO.putNext(combine, uniforms, FRAME_BLEND_UBO);
		try {
			writeBlendParams(ubo, inverseTotalWeight(sampleCount), sampleCount);
			RenderTarget fallback = HISTORY[WEIGHTED_INDICES[sampleCount - 1]];
			for (int i = 0; i < FRAME_BLEND_SAMPLE_LIMIT; i++) {
				RenderTarget target = i < sampleCount ? HISTORY[WEIGHTED_INDICES[i]] : fallback;
				MutableTextureInput input = HISTORY_INPUTS[i];
				if (input == null) {
					input = new MutableTextureInput(SAMPLE_NAMES[i], target);
					HISTORY_INPUTS[i] = input;
				} else {
					input.setTarget(target);
				}
				setSampler(pass, SAMPLE_NAMES[i], input);
			}
			combine.process(main, allocator);
		} catch (RuntimeException e) {
			if (COMBINE_UBO.resetIfClosed(e)) {
				return;
			}
			throw e;
		}
	}

	public static void applyAccumulationMax(GraphicsResourceAllocator allocator, float strength) {
		applyAccumulation(allocator, strength * 7.0f, ACCUMULATION_MAX_ID, "accumulation_max", true);
	}

	public static void applyAccumulationMix(GraphicsResourceAllocator allocator, float strength) {
		applyAccumulation(allocator, strength * 5.0f, ACCUMULATION_MIX_ID, "accumulation_mix", false);
	}

	public static void invalidate() {
		for (int i = 0; i < HISTORY.length; i++) {
			if (HISTORY[i] != null) {
				HISTORY[i].destroyBuffers();
				HISTORY[i] = null;
			}
			HISTORY_INPUTS[i] = null;
			HISTORY_TIMES[i] = 0.0;
		}
		if (accumRead != null) {
			accumRead.destroyBuffers();
			accumRead = null;
		}
		if (accumWrite != null) {
			accumWrite.destroyBuffers();
			accumWrite = null;
		}
		COMBINE_UBO.reset();
		ACCUM_UBO_BUF.reset();
		targetW = 0;
		targetH = 0;
		historyWriteIndex = 0;
		historyFilled = 0;
		smoothedFps = 0;
		Arrays.fill(WEIGHTED_INDICES, 0);
		Arrays.fill(WEIGHTED_WEIGHTS, 0.0f);
		Arrays.fill(COMPACT_INDICES, 0);
		Arrays.fill(COMPACT_WEIGHTS, 0.0f);
		cachedCombineChain = null;
		cachedAccumMax = null;
		cachedAccumMix = null;
		injectedMain = null;
		injectedPrev = null;
		accumHasPrevious = false;
	}

	public static float getHybridVelocityStrength(float fps, int refreshRate, float strength) {
		float base = Math.max(0.0f, strength);
		if (base == 0.0f || fps <= 0.0f || refreshRate <= 0) {
			return base;
		}
		float reference = Math.min(fps, (float) refreshRate);
		float frameSpan = base * (fps / reference);
		float filler = Math.min(1.0f, frameSpan);
		float blendFps = smoothedFps <= 0.0f ? fps : smoothedFps * 0.85f + fps * 0.15f;
		float blendRef = Math.min(blendFps, (float) refreshRate);
		float blendSpan = base * (blendFps / blendRef);
		int sampleCount = Math.min(MAX_HISTORY, (int) Math.ceil(blendSpan - 0.000001f));
		sampleCount = Math.min(sampleCount, Math.min(MAX_HISTORY, historyFilled + 1));
		if (sampleCount > FRAME_BLEND_SAMPLE_LIMIT) {
			int maxGap = 1;
			int previous = 0;
			for (int out = 1; out < FRAME_BLEND_SAMPLE_LIMIT; out++) {
				int source = Math.round((float) out * (sampleCount - 1) / (FRAME_BLEND_SAMPLE_LIMIT - 1));
				maxGap = Math.max(maxGap, source - previous);
				previous = source;
			}
			filler = Math.max(filler, maxGap);
		}
		return filler;
	}

	private static void updateSmoothedFps(float fps) {
		if (fps > 0.0f) {
			smoothedFps = smoothedFps <= 0.0f ? fps : smoothedFps * 0.85f + fps * 0.15f;
		}
	}

	private static void pushHistory(RenderTarget src, double timestamp) {
		if (HISTORY[historyWriteIndex] == null) {
			return;
		}
		copyTexture(src, HISTORY[historyWriteIndex]);
		HISTORY_TIMES[historyWriteIndex] = timestamp;
		historyWriteIndex = (historyWriteIndex + 1) % MAX_HISTORY;
		if (historyFilled < MAX_HISTORY) {
			historyFilled++;
		}
	}

	private static int buildWeightedSampleList(double exposureEnd, int refreshRate, float fps, float strength) {
		Arrays.fill(WEIGHTED_WEIGHTS, 0.0f);
		if (historyFilled <= 0) {
			return 0;
		}
		double reference = fps > 0.0f ? Math.min(fps, (double) refreshRate) : refreshRate;
		double duration = Math.max(0.0, strength) / reference;
		double start = exposureEnd - duration;
		double frameTime = fps > 0.0f ? 1.0 / fps : 1.0 / refreshRate;
		double total = 0.0;
		int count = 0;
		int first = historyWriteIndex - historyFilled;
		if (first < 0) {
			first += MAX_HISTORY;
		}
		for (int i = 0; i < historyFilled; i++) {
			int idx = (first + i) % MAX_HISTORY;
			double frameEnd = HISTORY_TIMES[idx];
			if (frameEnd <= 0.0) {
				continue;
			}
			double frameStart;
			if (i > 0) {
				frameStart = HISTORY_TIMES[(first + i - 1) % MAX_HISTORY];
			} else {
				frameStart = frameEnd - frameTime;
			}
			if (frameStart >= frameEnd) {
				frameStart = frameEnd - frameTime;
			}
			double overlap = Math.min(frameEnd, exposureEnd) - Math.max(frameStart, start);
			if (overlap > 0.0000001) {
				WEIGHTED_INDICES[count] = idx;
				WEIGHTED_WEIGHTS[count] = (float) overlap;
				total += overlap;
				count++;
			}
		}
		if (count <= 0 || total <= 0.0000001) {
			return 0;
		}
		if (count > FRAME_BLEND_SAMPLE_LIMIT) {
			return compact(count);
		}
		return count;
	}

	private static int compact(int sampleCount) {
		if (sampleCount <= FRAME_BLEND_SAMPLE_LIMIT) {
			return sampleCount;
		}
		if (FRAME_BLEND_SAMPLE_LIMIT == 1) {
			float total = 0.0f;
			for (int i = 0; i < sampleCount; i++) {
				total += WEIGHTED_WEIGHTS[i];
			}
			WEIGHTED_INDICES[0] = WEIGHTED_INDICES[sampleCount - 1];
			WEIGHTED_WEIGHTS[0] = total;
			return 1;
		}
		Arrays.fill(COMPACT_WEIGHTS, 0.0f);
		for (int out = 0; out < FRAME_BLEND_SAMPLE_LIMIT; out++) {
			int source = Math.round((float) out * (sampleCount - 1) / (FRAME_BLEND_SAMPLE_LIMIT - 1));
			COMPACT_INDICES[out] = WEIGHTED_INDICES[source];
		}
		for (int source = 0; source < sampleCount; source++) {
			int out = Math.round((float) source * (FRAME_BLEND_SAMPLE_LIMIT - 1) / (sampleCount - 1));
			out = Math.max(0, Math.min(FRAME_BLEND_SAMPLE_LIMIT - 1, out));
			COMPACT_WEIGHTS[out] += WEIGHTED_WEIGHTS[source];
		}
		for (int i = 0; i < FRAME_BLEND_SAMPLE_LIMIT; i++) {
			WEIGHTED_INDICES[i] = COMPACT_INDICES[i];
			WEIGHTED_WEIGHTS[i] = COMPACT_WEIGHTS[i];
		}
		return FRAME_BLEND_SAMPLE_LIMIT;
	}

	private static float inverseTotalWeight(int sampleCount) {
		float total = 0.0f;
		for (int i = 0; i < sampleCount; i++) {
			total += WEIGHTED_WEIGHTS[i];
		}
		return total > 0.0f ? 1.0f / total : 1.0f;
	}

	private static void applyAccumulation(
		GraphicsResourceAllocator allocator,
		float strength,
		Identifier id,
		String name,
		boolean max
	) {
		Minecraft client = Minecraft.getInstance();
		RenderTarget main = client.getMainRenderTarget();
		ensureTargets(main.width, main.height);
		if (!accumHasPrevious) {
			copyTexture(main, accumRead);
			accumHasPrevious = true;
			return;
		}
		PostChain chain = MotionBlurShaders.loadProcessor(client, id, name);
		if (chain == null) {
			return;
		}
		if (max && chain != cachedAccumMax) {
			cachedAccumMax = chain;
			injectedMain = null;
			injectedPrev = null;
		} else if (!max && chain != cachedAccumMix) {
			cachedAccumMix = chain;
			injectedMain = null;
			injectedPrev = null;
		}
		PostPass pass = firstPass(chain);
		if (pass == null) {
			return;
		}
		Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).getCustomUniforms();
		if (!uniforms.containsKey(ACCUM_UBO)) {
			return;
		}
		GpuBuffer ubo = ACCUM_UBO_BUF.put(chain, uniforms, ACCUM_UBO);
		try {
			writeFloat(ubo, (float) (1.0 - Math.pow(0.5, strength / 3.0)));
			if (injectedMain == null) {
				injectedMain = new MutableTextureInput(MAIN_SAMPLER, main);
			} else {
				injectedMain.setTarget(main);
			}
			setSampler(pass, MAIN_SAMPLER, injectedMain);
			if (injectedPrev == null) {
				injectedPrev = new MutableTextureInput(PREV_SAMPLER, accumRead);
			} else {
				injectedPrev.setTarget(accumRead);
			}
			setSampler(pass, PREV_SAMPLER, injectedPrev);
			chain.process(accumWrite, allocator);
			copyTexture(accumWrite, main);
			RenderTarget swap = accumRead;
			accumRead = accumWrite;
			accumWrite = swap;
		} catch (RuntimeException e) {
			if (ACCUM_UBO_BUF.resetIfClosed(e)) {
				return;
			}
			throw e;
		}
	}

	private static void ensureTargets(int w, int h) {
		if (targetW == w && targetH == h && HISTORY[0] != null && accumRead != null && accumWrite != null) {
			return;
		}
		for (int i = 0; i < HISTORY.length; i++) {
			if (HISTORY[i] != null) {
				HISTORY[i].destroyBuffers();
			}
			HISTORY[i] = new MainTarget(w, h);
			HISTORY_INPUTS[i] = null;
			HISTORY_TIMES[i] = 0.0;
		}
		if (accumRead != null) {
			accumRead.destroyBuffers();
		}
		if (accumWrite != null) {
			accumWrite.destroyBuffers();
		}
		accumRead = new MainTarget(w, h);
		accumWrite = new MainTarget(w, h);
		targetW = w;
		targetH = h;
		historyWriteIndex = 0;
		historyFilled = 0;
		Arrays.fill(WEIGHTED_INDICES, 0);
		Arrays.fill(WEIGHTED_WEIGHTS, 0.0f);
		Arrays.fill(COMPACT_INDICES, 0);
		Arrays.fill(COMPACT_WEIGHTS, 0.0f);
		injectedMain = null;
		injectedPrev = null;
		accumHasPrevious = false;
	}

	private static void copyTexture(RenderTarget src, RenderTarget dst) {
		if (src == null || dst == null || src.getColorTexture() == null || dst.getColorTexture() == null) {
			return;
		}
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
			src.getColorTexture(),
			dst.getColorTexture(),
			0,
			0,
			0,
			0,
			0,
			dst.width,
			dst.height
		);
	}

	private static PostPass firstPass(PostChain chain) {
		List<PostPass> passes = ((PostChainAccessor) chain).getPasses();
		return passes.isEmpty() ? null : passes.getFirst();
	}

	private static void setSampler(PostPass pass, String samplerName, PostPass.Input replacement) {
		List<PostPass.Input> inputs = ((PostPassAccessor) pass).getInputs();
		for (int i = 0; i < inputs.size(); i++) {
			if (samplerName.equals(inputs.get(i).samplerName())) {
				if (inputs.get(i) != replacement) {
					inputs.set(i, replacement);
				}
				return;
			}
		}
	}

	private static void writeFloat(GpuBuffer ubo, float value) {
		GpuBufferUtil.write(ubo, ACCUM_UBO_SIZE, builder -> {
			builder.putFloat(value);
			builder.putInt(0);
			builder.putInt(0);
			builder.putInt(0);
		});
	}

	private static void writeBlendParams(GpuBuffer ubo, float invTotalWeight, int sampleCount) {
		GpuBufferUtil.write(ubo, FRAME_BLEND_UBO_SIZE, builder -> {
			builder.putFloat(invTotalWeight);
			builder.putInt(sampleCount);
			for (int i = 0; i < FRAME_BLEND_SAMPLE_LIMIT; i++) {
				builder.putFloat(i < sampleCount ? WEIGHTED_WEIGHTS[i] : 0.0f);
			}
			builder.putFloat(0.0f);
			builder.putFloat(0.0f);
		});
	}

	private static final class MutableTextureInput implements PostPass.Input {
		private final String samplerName;
		private RenderTarget target;

		MutableTextureInput(String samplerName, RenderTarget target) {
			this.samplerName = samplerName;
			this.target = target;
		}

		void setTarget(RenderTarget target) {
			this.target = target;
		}

		@Override
		public void addToPass(FramePass pass, Map<Identifier, ResourceHandle<RenderTarget>> targets) {
		}

		@Override
		public GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> targets) {
			return target.getColorTextureView();
		}

		@Override
		public String samplerName() {
			return samplerName;
		}

		@Override
		public boolean bilinear() {
			return false;
		}
	}
}
