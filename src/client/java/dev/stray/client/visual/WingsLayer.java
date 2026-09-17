package dev.stray.client.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.stray.Stray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Feathered wings on the upper back. Each feather is a textured blade fanned
 * from a root behind the shoulders; the pair flaps slowly at rest and beats
 * harder while sprinting or gliding. Geometry is built from a runtime texture,
 * tinted by the shop color.
 */
public final class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier FEATHER = Stray.id("dynamic/wing_feather");
	private static final int TEX_W = 16;
	private static final int TEX_H = 64;
	private static boolean textureReady;

	public WingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (!(state instanceof WingsHolder holder)) {
			return;
		}
		ShopWings.Wings wings = holder.stray$wings();
		if (wings == null || state.isInvisible || state.isSpectator) {
			return;
		}
		ensureTexture();
		ShopWings.Style style = wings.style();
		RenderType type = style.glow ? RenderTypes.entityTranslucentEmissive(FEATHER) : RenderTypes.entityTranslucent(FEATHER);
		int packedLight = style.glow ? 0xF000F0 : light;
		float time = state.ageInTicks;
		float walk = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
		float glide = state.fallFlyingTimeInTicks > 0 ? 1f : 0f;
		float beatRate = 0.09f + walk * 0.16f + glide * 0.22f;
		float beatAmp = 5f + walk * 16f + glide * 24f;
		float beat = Mth.sin(state.ageInTicks * beatRate) * beatAmp;
		float open = 32f + walk * 18f + glide * 26f + beat;
		float lift = -6f - walk * 6f - glide * 10f + beat * 0.35f;

		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.translate(0f, 0.16f, 0.17f);
		for (int side = -1; side <= 1; side += 2) {
			drawWing(pose, collector, type, packedLight, wings, side, open, lift, time);
		}
		pose.popPose();
	}

	private void drawWing(
		PoseStack pose,
		SubmitNodeCollector collector,
		RenderType type,
		int light,
		ShopWings.Wings wings,
		int side,
		float open,
		float lift,
		float time
	) {
		ShopWings.Style style = wings.style();
		int n = style.feathers;
		pose.pushPose();
		pose.translate(side * 0.08f, 0f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(side * open));
		pose.mulPose(Axis.ZP.rotationDegrees(-side * lift));
		for (int i = 0; i < n; i++) {
			float t = n == 1 ? 0f : i / (float) (n - 1);
			float fan = Mth.lerp(t, -28f, 78f);
			float length = (0.62f + (1f - Math.abs(t - 0.35f)) * 0.45f) * style.span;
			float width = 0.12f + (1f - t) * 0.05f;
			float flutter = Mth.sin(time * 0.03f + i * 0.9f) * 2.5f;
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(side * (fan + flutter)));
			pose.mulPose(Axis.XP.rotationDegrees(side * (6f + t * 10f)));
			float shade = 1f - t * 0.18f;
			float alpha = style.alpha * (style == ShopWings.Style.FAIRY ? 0.8f + 0.2f * (1f - t) : 1f);
			int color = tint(wings.rgb(), shade, alpha);
			int shaft = tint(wings.rgb(), shade * 0.72f, alpha);
			float len = length;
			float wid = width;
			int col = color;
			int shaftCol = shaft;
			int packed = light;
			collector.submitCustomGeometry(pose, type, (p, consumer) -> blade(p, consumer, len, wid, col, shaftCol, packed, side));
			pose.popPose();
		}
		pose.popPose();
	}

	private static void blade(PoseStack.Pose p, VertexConsumer consumer, float length, float width, int color, int shaftColor, int light, int side) {
		float x0 = 0f;
		float x1 = side * length;
		float y0 = -width * 0.35f;
		float y1 = width * 0.65f;
		float u0 = side > 0 ? 0f : 1f;
		float u1 = side > 0 ? 1f : 0f;
		quad(p, consumer, x0, y0, x1, y1, u0, u1, color, light, false);
		quad(p, consumer, x0, y0, x1, y1, u0, u1, color, light, true);
		float sy0 = -width * 0.04f;
		float sy1 = width * 0.06f;
		quad(p, consumer, x0, sy0, x1 * 0.92f, sy1, u0, u1, shaftColor, light, false);
		quad(p, consumer, x0, sy0, x1 * 0.92f, sy1, u0, u1, shaftColor, light, true);
	}

	private static void quad(
		PoseStack.Pose p,
		VertexConsumer consumer,
		float x0,
		float y0,
		float x1,
		float y1,
		float u0,
		float u1,
		int color,
		int light,
		boolean back
	) {
		float nz = back ? -1f : 1f;
		float z = back ? 0.0015f : -0.0015f;
		if (!back) {
			vertex(p, consumer, x0, y0, z, u0, 0f, color, light, nz);
			vertex(p, consumer, x0, y1, z, u0, 1f, color, light, nz);
			vertex(p, consumer, x1, y1, z, u1, 1f, color, light, nz);
			vertex(p, consumer, x1, y0, z, u1, 0f, color, light, nz);
		} else {
			vertex(p, consumer, x1, y0, z, u1, 0f, color, light, nz);
			vertex(p, consumer, x1, y1, z, u1, 1f, color, light, nz);
			vertex(p, consumer, x0, y1, z, u0, 1f, color, light, nz);
			vertex(p, consumer, x0, y0, z, u0, 0f, color, light, nz);
		}
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer consumer, float x, float y, float z, float u, float v, int color, int light, float nz) {
		consumer.addVertex(p, x, y, z)
			.setColor(color)
			.setUv(v, u)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(p, 0f, 0f, nz);
	}

	private static int tint(int rgb, float shade, float alpha) {
		int r = Math.round(((rgb >> 16) & 0xFF) * shade);
		int g = Math.round(((rgb >> 8) & 0xFF) * shade);
		int b = Math.round((rgb & 0xFF) * shade);
		int a = Math.round(Mth.clamp(alpha, 0f, 1f) * 255f);
		return (a << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
	}

	private static void ensureTexture() {
		if (textureReady) {
			return;
		}
		textureReady = true;
		NativeImage image = new NativeImage(TEX_W, TEX_H, false);
		for (int y = 0; y < TEX_H; y++) {
			float v = y / (float) (TEX_H - 1);
			float taper = 1f - (float) Math.pow(Math.max(0f, v - 0.55f) / 0.45f, 1.8);
			float halfWidth = Mth.clamp(taper * 0.5f + 0.05f * (1f - v), 0.04f, 0.5f);
			for (int x = 0; x < TEX_W; x++) {
				float u = (x + 0.5f) / TEX_W;
				float dist = Math.abs(u - 0.5f);
				float edge = halfWidth - dist;
				float alpha = Mth.clamp(edge * TEX_W * 0.9f, 0f, 1f);
				float base = 0.88f + 0.12f * (1f - v);
				if (v < 0.08f) {
					alpha *= v / 0.08f;
				}
				int a = Math.round(alpha * 255f);
				int c = Math.round(base * 255f);
				image.setPixel(x, y, (a << 24) | (c << 16) | (c << 8) | c);
			}
		}
		Minecraft.getInstance().getTextureManager().register(FEATHER, new DynamicTexture(() -> "stray-wing-feather", image));
	}
}
