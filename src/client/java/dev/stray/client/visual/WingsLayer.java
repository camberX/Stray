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
 * from a root behind the shoulders. Both sides use the same +X mesh; the right
 * wing is mirrored with a pose scale so winding, UVs, and lighting stay even.
 * The pair idles with a slow flap and only beats harder while gliding — walk
 * animation speed is ignored so tapping W does not twitch the wings.
 */
public final class WingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier FEATHER = Stray.id("dynamic/wing_feather");
	private static final Identifier BUTTERFLY = Stray.id("textures/entity/butterfly_wing.png");
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
		ShopWings.Style style = wings.style();
		float time = state.ageInTicks;
		float glide = state.isFallFlying ? 1f : 0f;
		if (style.butterfly) {
			RenderType type = RenderTypes.entityTranslucent(BUTTERFLY);
			float flutter = Mth.sin(time * 0.19f) * 6.5f + Mth.sin(time * 0.43f) * 2.4f;
			float open = 15f + glide * 12f + flutter;
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			pose.translate(0f, 0.38f, 0.15f);
			int color = tint(wings.rgb(), 1f, style.alpha);
			for (int side = -1; side <= 1; side += 2) {
				drawButterfly(pose, collector, type, light, color, style.span, side, open);
			}
			pose.popPose();
			return;
		}
		ensureTexture();
		RenderType type = style.glow ? RenderTypes.entityTranslucentEmissive(FEATHER) : RenderTypes.entityTranslucent(FEATHER);
		int packedLight = style.glow ? 0xF000F0 : light;
		float beatRate = 0.08f + glide * 0.18f;
		float beatAmp = 4.2f + glide * 22f;
		float beat = Mth.sin(time * beatRate) * beatAmp;
		float open = 36f + glide * 28f + beat;
		float lift = 10f + glide * 14f + beat * 0.35f;

		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.translate(0f, 0.16f, 0.17f);
		for (int side = -1; side <= 1; side += 2) {
			drawWing(pose, collector, type, packedLight, wings, side, open, lift, time);
		}
		pose.popPose();
	}

	private void drawButterfly(
		PoseStack pose,
		SubmitNodeCollector collector,
		RenderType type,
		int light,
		int color,
		float span,
		int side,
		float open
	) {
		pose.pushPose();
		pose.translate(side * 0.05f, 0f, 0f);
		pose.scale(side, 1f, 1f);
		pose.mulPose(Axis.YP.rotationDegrees(-open));
		pose.mulPose(Axis.XP.rotationDegrees(5f));
		float x1 = 0.90f * span;
		float y0 = -0.72f;
		float y1 = 0.58f;
		int packed = light;
		int col = color;
		collector.submitCustomGeometry(pose, type, (p, consumer) -> {
			butterflyQuad(p, consumer, 0f, y0, x1, y1, col, packed, false);
			butterflyQuad(p, consumer, 0f, y0, x1, y1, col, packed, true);
		});
		pose.popPose();
	}

	private static void butterflyQuad(
		PoseStack.Pose p,
		VertexConsumer consumer,
		float x0,
		float y0,
		float x1,
		float y1,
		int color,
		int light,
		boolean back
	) {
		float nz = back ? -1f : 1f;
		float z = back ? 0.0015f : -0.0015f;
		if (!back) {
			wingVertex(p, consumer, x0, y0, z, 0f, 0f, color, light, nz);
			wingVertex(p, consumer, x0, y1, z, 0f, 1f, color, light, nz);
			wingVertex(p, consumer, x1, y1, z, 1f, 1f, color, light, nz);
			wingVertex(p, consumer, x1, y0, z, 1f, 0f, color, light, nz);
		} else {
			wingVertex(p, consumer, x1, y0, z, 1f, 0f, color, light, nz);
			wingVertex(p, consumer, x1, y1, z, 1f, 1f, color, light, nz);
			wingVertex(p, consumer, x0, y1, z, 0f, 1f, color, light, nz);
			wingVertex(p, consumer, x0, y0, z, 0f, 0f, color, light, nz);
		}
	}

	private static void wingVertex(PoseStack.Pose p, VertexConsumer consumer, float x, float y, float z, float u, float v, int color, int light, float nz) {
		consumer.addVertex(p, x, y, z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(p, 0f, 0f, nz);
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
		int n = Math.max(1, style.feathers);
		pose.pushPose();
		pose.translate(side * 0.08f, 0f, 0f);
		// Mirror the right wing in X so blades always extend in local +X. Open/lift
		// then sweep both sides toward +z (behind the back) with the same signs.
		pose.scale(side, 1f, 1f);
		pose.mulPose(Axis.YP.rotationDegrees(-open));
		pose.mulPose(Axis.ZP.rotationDegrees(-lift));
		if (n > 1) {
			for (int i = 0; i < n - 1; i++) {
				float t = (i + 0.5f) / (n - 1);
				feather(pose, collector, type, light, wings, t, 0.62f, true, time, i + 17);
			}
		}
		for (int i = 0; i < n; i++) {
			float t = n == 1 ? 1f : i / (float) (n - 1);
			feather(pose, collector, type, light, wings, t, 1f, false, time, i);
		}
		pose.popPose();
	}

	private void feather(
		PoseStack pose,
		SubmitNodeCollector collector,
		RenderType type,
		int light,
		ShopWings.Wings wings,
		float t,
		float size,
		boolean covert,
		float time,
		int seed
	) {
		ShopWings.Style style = wings.style();
		// Player renderer scales Y by -1, so model +Y is toward the feet. Negative
		// fan aims at the head. t=0 is the short bottom covert, t=1 the top primary.
		float fan = Mth.lerp(t, 18f, -40f);
		float length = (0.34f + (float) Math.pow(t, 1.2) * 0.92f) * style.span * size;
		float width = (0.10f + (1f - t) * 0.045f) * (covert ? 0.72f : 1f);
		float flutter = Mth.sin(time * 0.03f + seed * 0.9f) * (covert ? 3.2f : 2.2f);
		pose.pushPose();
		if (covert) {
			pose.translate(0f, 0f, 0.008f);
		}
		pose.mulPose(Axis.ZP.rotationDegrees(fan + flutter));
		pose.mulPose(Axis.XP.rotationDegrees(4f + t * 5f));
		float shade = (1f - t * 0.14f) * (covert ? 0.78f : 1f);
		float alpha = style.alpha * (style == ShopWings.Style.FAIRY ? 0.8f + 0.2f * (1f - t) : 1f);
		if (covert) {
			alpha *= 0.88f;
		}
		int color = tint(wings.rgb(), shade, alpha);
		int shaft = tint(wings.rgb(), shade * 0.72f, alpha);
		float len = length;
		float wid = width;
		int col = color;
		int shaftCol = shaft;
		int packed = light;
		collector.submitCustomGeometry(pose, type, (p, consumer) -> blade(p, consumer, len, wid, col, shaftCol, packed));
		pose.popPose();
	}

	private static void blade(PoseStack.Pose p, VertexConsumer consumer, float length, float width, int color, int shaftColor, int light) {
		float x0 = 0f;
		float x1 = length;
		float y0 = -width * 0.35f;
		float y1 = width * 0.65f;
		quad(p, consumer, x0, y0, x1, y1, color, light, false);
		quad(p, consumer, x0, y0, x1, y1, color, light, true);
		float sy0 = -width * 0.04f;
		float sy1 = width * 0.06f;
		quad(p, consumer, x0, sy0, x1 * 0.92f, sy1, shaftColor, light, false);
		quad(p, consumer, x0, sy0, x1 * 0.92f, sy1, shaftColor, light, true);
	}

	private static void quad(
		PoseStack.Pose p,
		VertexConsumer consumer,
		float x0,
		float y0,
		float x1,
		float y1,
		int color,
		int light,
		boolean back
	) {
		float nz = back ? -1f : 1f;
		float z = back ? 0.0015f : -0.0015f;
		if (!back) {
			vertex(p, consumer, x0, y0, z, 0f, 0f, color, light, nz);
			vertex(p, consumer, x0, y1, z, 0f, 1f, color, light, nz);
			vertex(p, consumer, x1, y1, z, 1f, 1f, color, light, nz);
			vertex(p, consumer, x1, y0, z, 1f, 0f, color, light, nz);
		} else {
			vertex(p, consumer, x1, y0, z, 1f, 0f, color, light, nz);
			vertex(p, consumer, x1, y1, z, 1f, 1f, color, light, nz);
			vertex(p, consumer, x0, y1, z, 0f, 1f, color, light, nz);
			vertex(p, consumer, x0, y0, z, 0f, 0f, color, light, nz);
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
