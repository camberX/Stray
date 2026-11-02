package dev.stray.client.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Chest cosmetic in the style of the Female Gender Mod (Stray's own geometry, not its code):
 * two boxes on the upper torso, tilted down and bouncing a little while walking. They are
 * textured from the player's skin (and jacket layer), or from the chestplate when one is worn,
 * using the same body area of the texture so they match the shirt or armor.
 */
public final class ChestLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Body area of the torso front in skin / armor textures: x 20..28, y 20..32 (64 wide). */
	private static final float BODY_U = 20f;
	private static final float BODY_V = 20f;
	/** Breast box in torso pixels: top edge, height, depth. */
	private static final float TOP = 2.5f;
	private static final float HEIGHT = 4f;
	private static final float DEPTH = 2.5f;
	private static final float TILT = 22f;
	/** Each side turns this far outward around the middle, so the pair doesn't read as one shelf. */
	private static final float SPLAY = 8f;

	private final EquipmentAssetManager equipment;

	public ChestLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EquipmentAssetManager equipment) {
		super(parent);
		this.equipment = equipment;
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (!(state instanceof ChestHolder holder) || !holder.stray$chest() || state.isInvisible || state.isSpectator || state.skin == null) {
			return;
		}
		float swing = state.walkAnimationSpeed;
		float bounce = Mth.sin(state.walkAnimationPos * 1.3324f) * swing * 7f;
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		ItemStack chest = state.chestEquipment;
		boolean armored = drawArmor(pose, collector, light, chest, bounce);
		if (!armored) {
			Identifier skin = state.skin.body().texturePath();
			RenderType type = RenderTypes.entityTranslucent(skin);
			drawPair(pose, collector, type, light, -1, 64f, 64f, BODY_U, BODY_V, 0f, bounce);
			if (state.showJacket) {
				// Jacket (outer) layer: same area 16 rows lower, a quarter pixel bigger.
				drawPair(pose, collector, type, light, -1, 64f, 64f, BODY_U, BODY_V + 16f, 0.25f, bounce);
			}
		}
		pose.popPose();
	}

	/** Draws the chestplate's own texture (and dye) over the boxes; false when no armor is worn. */
	private boolean drawArmor(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack chest, float bounce) {
		if (chest == null || chest.isEmpty()) {
			return false;
		}
		Equippable equippable = chest.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.CHEST || equippable.assetId().isEmpty()) {
			return false;
		}
		EquipmentClientInfo info = equipment.get(equippable.assetId().get());
		boolean drew = false;
		for (EquipmentClientInfo.Layer layer : info.getLayers(EquipmentClientInfo.LayerType.HUMANOID)) {
			int color = -1;
			if (layer.dyeable().isPresent()) {
				int undyed = layer.dyeable().get().colorWhenUndyed().map(c -> 0xFF000000 | c).orElse(0);
				int dyed = DyedItemColor.getOrDefault(chest, undyed);
				if (dyed == 0) {
					continue;
				}
				color = 0xFF000000 | dyed;
			}
			RenderType type = RenderTypes.armorCutoutNoCull(layer.getTextureLocation(EquipmentClientInfo.LayerType.HUMANOID));
			drawPair(pose, collector, type, light, color, 64f, 32f, BODY_U, BODY_V, 1f, bounce);
			drew = true;
		}
		return drew;
	}

	private static void drawPair(
		PoseStack pose,
		SubmitNodeCollector collector,
		RenderType type,
		int light,
		int color,
		float texW,
		float texH,
		float u,
		float v,
		float grow,
		float bounce
	) {
		for (int side = 0; side < 2; side++) {
			float x0 = side == 0 ? -4f : 0f;
			float x1 = x0 + 4f;
			float tilt = TILT + (side == 0 ? bounce : -bounce * 0.8f);
			pose.pushPose();
			// Pivot on the inner edge so each side turns slightly outward.
			pose.translate(0f, TOP, -2f);
			//? if >=26.3 {
			pose.rotate(Axis.YP.rotationDegrees(side == 0 ? -SPLAY : SPLAY));
			pose.rotate(Axis.XP.rotationDegrees(tilt));
			//?} else {
			/*pose.mulPose(Axis.YP.rotationDegrees(side == 0 ? -SPLAY : SPLAY));
			pose.mulPose(Axis.XP.rotationDegrees(tilt));*///?}
			float bu0 = u + (x0 + 4f);
			float bu1 = u + (x1 + 4f);
			float bv0 = v + TOP;
			float bv1 = v + TOP + HEIGHT;
			float gx0 = x0 - grow;
			float gx1 = x1 + grow;
			float gy0 = -grow;
			float gy1 = HEIGHT + grow;
			float gz0 = -DEPTH - grow;
			float gz1 = 0.5f;
			collector.submitCustomGeometry(pose, type, (p, consumer) -> box(p, consumer, gx0, gy0, gz0, gx1, gy1, gz1, bu0, bv0, bu1, bv1, texW, texH, color, light));
			pose.popPose();
		}
	}

	/**
	 * A box whose front is the given texture area. The top, bottom and side faces stretch the
	 * front's edge pixels, so the shirt or armor pattern runs over the curve without seams.
	 */
	private static void box(
		PoseStack.Pose p,
		VertexConsumer c,
		float x0, float y0, float z0, float x1, float y1, float z1,
		float u0, float v0, float u1, float v1,
		float texW, float texH,
		int color, int light
	) {
		float a = u0 / texW;
		float b = u1 / texW;
		float t = v0 / texH;
		float d = v1 / texH;
		float px = 1f / texW;
		float py = 1f / texH;
		// Front (faces -z).
		quad(p, c, color, light, 0, 0, -1,
			x0, y0, z0, a, t,
			x0, y1, z0, a, d,
			x1, y1, z0, b, d,
			x1, y0, z0, b, t);
		// Top (faces -y): the front's top row.
		quad(p, c, color, light, 0, -1, 0,
			x0, y0, z1, a, t,
			x0, y0, z0, a, t + py,
			x1, y0, z0, b, t + py,
			x1, y0, z1, b, t);
		// Bottom (faces +y): the front's bottom row.
		quad(p, c, color, light, 0, 1, 0,
			x0, y1, z0, a, d - py,
			x0, y1, z1, a, d,
			x1, y1, z1, b, d,
			x1, y1, z0, b, d - py);
		// Sides (face -x / +x): the front's edge columns.
		quad(p, c, color, light, -1, 0, 0,
			x0, y0, z1, a, t,
			x0, y1, z1, a, d,
			x0, y1, z0, a + px, d,
			x0, y0, z0, a + px, t);
		quad(p, c, color, light, 1, 0, 0,
			x1, y0, z0, b - px, t,
			x1, y1, z0, b - px, d,
			x1, y1, z1, b, d,
			x1, y0, z1, b, t);
	}

	private static void quad(
		PoseStack.Pose p, VertexConsumer c, int color, int light, float nx, float ny, float nz,
		float x0, float y0, float z0, float u0, float v0,
		float x1, float y1, float z1, float u1, float v1,
		float x2, float y2, float z2, float u2, float v2,
		float x3, float y3, float z3, float u3, float v3
	) {
		vertex(p, c, x3, y3, z3, u3, v3, color, light, nx, ny, nz);
		vertex(p, c, x2, y2, z2, u2, v2, color, light, nx, ny, nz);
		vertex(p, c, x1, y1, z1, u1, v1, color, light, nx, ny, nz);
		vertex(p, c, x0, y0, z0, u0, v0, color, light, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer c, float x, float y, float z, float u, float v, int color, int light, float nx, float ny, float nz) {
		c.addVertex(p, x, y, z)
			.setColor(color)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(p, nx, ny, nz);
	}
}
