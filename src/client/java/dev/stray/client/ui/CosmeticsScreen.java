package dev.stray.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.stray.client.input.MouseButtons;
import dev.stray.client.render.GuiDraw;
import dev.stray.client.util.FilePicker;
import dev.stray.client.visual.CosmeticsApi;
import dev.stray.client.visual.CustomCape;
import dev.stray.client.visual.ShopCape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;

/**
 * Cosmetics: a live 3D preview of your player on the left, and on the right the cosmetics you
 * have with their settings (Stray skin, cape, wings, chest). Every change shows in the preview
 * at once and is sent to stray.gay a moment later (see {@link CosmeticsApi}). The preview turns
 * to face whatever you are editing; drag it to look around.
 */
public final class CosmeticsScreen extends SubScreen {
	private static final String[] WING_STYLES = {"angel", "demon", "fairy", "phoenix", "butterfly"};
	private static final String[] WING_NAMES = {"Angel", "Demon", "Fairy", "Phoenix", "Butterfly"};
	private static final int[] WING_DEFAULTS = {0xF4F6FF, 0x2B1D2E, 0x9AE6FF, 0xFF7A1A, 0x3D98CB};
	private static final int[] SWATCHES = {
		0xF4F6FF, 0x2B1D2E, 0x9AE6FF, 0xFF7A1A, 0x3D98CB, 0xFF4F8B,
		0xB57BFF, 0x52E08A, 0xFFD34F, 0xE8414B, 0x1C1C1C, 0xC9CED6
	};
	private static final float PREVIEW_W = 150f;
	private static final float AREA_H = 270f;
	private static final float CARD_PAD = 10f;

	/** Preview yaw: where it is, where it is turning to, and whether you are dragging it. */
	private float yaw = 25f;
	private float targetYaw = 25f;
	private float pitch = 6f;
	private boolean draggingModel;
	private long lastDragAt;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;
	private boolean pickingSkin;
	private String focus = "skin";

	public CosmeticsScreen(Screen parent) {
		super("Cosmetics", parent, 480);
		CosmeticsApi.refresh();
	}

	@Override
	protected String subtitle() {
		return "only players with Stray see these";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		yaw += Mth.wrapDegrees(targetYaw - yaw) * Math.min(1f, delta * 0.25f + 0.08f);
	}

	@Override
	protected float body(float x, float y, float w) {
		float h = Math.max(140f, Math.min(AREA_H, room(y, 14f + 6f)));
		float pw = Math.min(PREVIEW_W, w * 0.34f);
		drawPreview(x, y, pw, h);
		float lx = x + pw + 12f;
		float lw = x + w - lx;
		scrollArea("cosmetics", lx, y, lw, h, (cx, cy, cw) -> {
			CosmeticsApi.Mine mine = CosmeticsApi.mine();
			if (mine == null) {
				text(CosmeticsApi.loading() ? "Logging in to stray.gay…" : "Couldn't load your cosmetics.", cx + 4f, cy, 20f, MUTED);
				if (!CosmeticsApi.loading()) {
					cy += 22f;
					button("Try again", cx + 4f, cy, Style.SECONDARY, CosmeticsApi::refresh);
					cy += BUTTON;
				}
				return cy + 20f;
			}
			cy = skinCard(mine, cx, cy, cw);
			cy = capeCard(mine, cx, cy, cw);
			cy = wingsCard(mine, cx, cy, cw);
			return chestCard(mine, cx, cy, cw);
		});
		y += h + 4f;
		String status = CosmeticsApi.status();
		return hint(status.isBlank() ? "Changes save on their own" : status, x, y + 2f, w, false);
	}

	// ------------------------------------------------------------------ preview

	private void drawPreview(float x, float y, float w, float h) {
		previewX = x;
		previewY = y;
		previewW = w;
		previewH = h;
		GuiDraw.roundedFine(graphics, x, y, w, h, ShadowLook.CONTROL_R, 0x0AFFFFFF);
		LocalPlayer player = minecraft.player;
		if (player == null) {
			GuiDraw.small(graphics, font, "Join a world", x + (w - GuiDraw.smallWidth(font, "Join a world")) / 2f, y + h / 2f - 8, MUTED);
			GuiDraw.small(graphics, font, "to see the preview", x + (w - GuiDraw.smallWidth(font, "to see the preview")) / 2f, y + h / 2f + 2, MUTED);
			return;
		}
		if (!draggingModel && Util.getMillis() - lastDragAt > 2500L) {
			targetYaw = focusYaw();
		}
		int x0 = Math.round(x + 2);
		int y0 = Math.round(y + 2);
		int x1 = Math.round(x + w - 2);
		int y1 = Math.round(y + h - 14);
		int scale = Math.round(Math.min((y1 - y0) / 2.4f, (x1 - x0) / 1.4f));
		renderPlayer(graphics, player, x0, y0, x1, y1, scale, yaw, pitch);
		String drag = "DRAG TO TURN";
		GuiDraw.small(graphics, font, drag, x + (w - GuiDraw.smallWidth(font, drag)) / 2f, y + h - 12, FAINT);
	}

	/** The side of the player that shows what you are editing. */
	private float focusYaw() {
		return switch (focus) {
			case "cape" -> 200f;
			case "wings" -> 150f;
			case "chest" -> 35f;
			default -> 25f;
		};
	}

	/** Like the inventory's player preview, but turned by {@code yaw} instead of following the mouse. */
	private static void renderPlayer(GuiGraphicsExtractor graphics, LocalPlayer player, int x0, int y0, int x1, int y1, int scale, float yaw, float pitch) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LocalPlayer, ?> renderer = dispatcher.getRenderer(player);
		EntityRenderState state = renderer.createRenderState(player, 1f);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180f + yaw;
			living.yRot = 0f;
			living.xRot = 0f;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1f;
			// A slow idle walk so the chest and wings move a little.
			living.walkAnimationPos = (Util.getMillis() % 100_000L) / 220f;
			living.walkAnimationSpeed = 0.25f;
		}
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
		pose.mul(camera);
		Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
		graphics.entity(state, scale, offset, pose, camera, x0, y0, x1, y1);
	}

	// ------------------------------------------------------------------ cards

	/** A cosmetic's card: title, a note on the right, then {@code bodyH} of controls. Returns the body's y. */
	private float card(String id, String title, String note, float x, float y, float w, float bodyH) {
		float h = 30f + bodyH + CARD_PAD;
		if (hovered(x, y, w, h) && !draggingModel) {
			focus = id;
		}
		ShadowLook.card(graphics, x, y, w, h, ShadowLook.CONTROL_R, focus.equals(id), false);
		text(title, x + CARD_PAD, y + 6f, 18f, TEXT);
		if (note != null && !note.isBlank()) {
			String shown = fit(note, w - CARD_PAD * 2 - textWidth(title) - 12f);
			GuiDraw.small(graphics, font, shown, x + w - CARD_PAD - GuiDraw.smallWidth(font, shown), smallMiddle(y + 6f, 18f), MUTED);
		}
		return y + 30f;
	}

	private float end(float y, float bodyH) {
		return y + 30f + bodyH + CARD_PAD + 6f;
	}

	private float note(String value, float x, float y, float w) {
		GuiDraw.small(graphics, font, dev.stray.client.util.TextFit.small(font, value, (int) (w - CARD_PAD * 2)), x + CARD_PAD, y + 3f, FAINT);
		return y + 12f;
	}

	private float skinCard(CosmeticsApi.Mine mine, float x, float y, float w) {
		boolean slim = currentBool("skin", "slim", mine.skinSlim());
		String sub = mine.skinHas() ? (slim ? "Stray skin · slim arms" : "Stray skin · classic arms") : "Your Minecraft skin";
		float bodyH = BUTTON + 4f + 12f;
		float top = y;
		y = card("skin", "Skin", sub, x, y, w, bodyH);
		float bx = x + CARD_PAD;
		bx += button(pickingSkin ? "Choosing…" : "Choose PNG", bx, y, Style.SECONDARY, this::pickSkin) + GAP;
		if (mine.skinHas()) {
			float cy = y + (BUTTON - 18f) * 0.5f;
			bx += chip("Classic", bx, cy, !slim, () -> CosmeticsApi.change("skin", "slim", new JsonPrimitive(false))) + 3f;
			bx += chip("Slim", bx, cy, slim, () -> CosmeticsApi.change("skin", "slim", new JsonPrimitive(true))) + GAP;
			button("Remove", bx, y, Style.DANGER, CosmeticsApi::removeSkin);
		}
		String limit = mine.skinRetry() > 0 ? "Next upload in " + mine.skinRetry() + "s" : mine.skinLeft() + " uploads left today";
		note("64x64 PNG; your real skin stays the same. " + limit + ".", x, y + BUTTON + 4f, w);
		return end(top, bodyH);
	}

	private float capeCard(CosmeticsApi.Mine mine, float x, float y, float w) {
		float top = y;
		if (!mine.whitelisted()) {
			y = card("cape", "Cape", "Not on the cape list", x, y, w, 12f);
			note("Capes are given out on stray.gay.", x, y, w);
			return end(top, 12f);
		}
		String sub = mine.hasCape() ? "Live" : "No cape yet";
		if (mine.capeRetry() > 0) {
			sub += " · change in " + duration(mine.capeRetry());
		}
		float bodyH = BUTTON + 4f + 12f;
		y = card("cape", "Cape", sub, x, y, w, bodyH);
		float bx = x + CARD_PAD;
		bx += button("Cape creator", bx, y, Style.SECONDARY, CustomCape::pickCreate) + GAP;
		if (CustomCape.ready()) {
			bx += button("Publish", bx, y, Style.PRIMARY, () -> {
				ShopCape.publish(CustomCape.png());
				Util.nonCriticalIoPool().execute(() -> {
					try {
						Thread.sleep(1500L);
					} catch (InterruptedException ignored) {
					}
					CosmeticsApi.refresh();
				});
			}) + GAP;
		}
		if (mine.hasCape()) {
			button("Remove", bx, y, Style.DANGER, () -> {
				ShopCape.unpublish();
				CosmeticsApi.refresh();
			});
		}
		String status = ShopCape.publishStatus();
		note(status.isBlank() ? "Make a cape from any image, then publish it." : status, x, y + BUTTON + 4f, w);
		return end(top, bodyH);
	}

	private float wingsCard(CosmeticsApi.Mine mine, float x, float y, float w) {
		float top = y;
		if (!mine.wingsGranted()) {
			y = card("wings", "Wings", "Not unlocked", x, y, w, 12f);
			note("Wings are given out on stray.gay.", x, y, w);
			return end(top, 12f);
		}
		boolean hidden = currentBool("wings", "hidden", mine.wingsHidden());
		String style = currentText("wings", "style", mine.wingsStyle());
		String rgb = currentText("wings", "rgb", mine.wingsRgb());
		// Style chips wrap to as many lines as they need.
		float inner = w - CARD_PAD * 2;
		int lines = 1;
		float run = chipWidth(hidden ? "Show" : "Hide") + 8f;
		for (String name : WING_NAMES) {
			float cw = chipWidth(name) + 3f;
			if (run + cw > inner + 3f) {
				lines++;
				run = 0f;
			}
			run += cw;
		}
		float chipsH = lines * 21f - 3f;
		float bodyH = chipsH + 6f + 14f + 4f + 12f;
		y = card("wings", "Wings", hidden ? "Hidden" : "Showing", x, y, w, bodyH);
		float cx = x + CARD_PAD;
		float cy = y;
		cx += chip(hidden ? "Show" : "Hide", cx, cy, false, () -> CosmeticsApi.change("wings", "hidden", new JsonPrimitive(!currentBool("wings", "hidden", mine.wingsHidden())))) + 8f;
		for (int i = 0; i < WING_STYLES.length; i++) {
			String id = WING_STYLES[i];
			String defaultRgb = String.format("%06X", WING_DEFAULTS[i]);
			if (cx + chipWidth(WING_NAMES[i]) > x + w - CARD_PAD) {
				cx = x + CARD_PAD;
				cy += 21f;
			}
			cx += chip(WING_NAMES[i], cx, cy, id.equals(style), () -> {
				// A new style starts in its own color, like on the desk; pick a swatch after.
				CosmeticsApi.change("wings", "style", new JsonPrimitive(id));
				CosmeticsApi.change("wings", "rgb", new JsonPrimitive(defaultRgb));
			}) + 3f;
		}
		float sy = y + chipsH + 6f;
		float sx = x + CARD_PAD;
		float size = 14f;
		for (int color : SWATCHES) {
			if (sx + size > x + w - CARD_PAD) {
				break;
			}
			String hex = String.format("%06X", color);
			boolean on = hex.equalsIgnoreCase(rgb);
			boolean hot = hovered(sx, sy, size, size);
			if (on || hot) {
				GuiDraw.roundedFine(graphics, sx - 2f, sy - 2f, size + 4f, size + 4f, 5f, on ? 0xFFEEF1F5 : 0x60FFFFFF);
			}
			GuiDraw.roundedFine(graphics, sx, sy, size, size, 4f, 0xFF000000 | color);
			hit(sx, sy, size, size, () -> CosmeticsApi.change("wings", "rgb", new JsonPrimitive(hex)));
			sx += size + 5f;
		}
		note("Everyone with Stray sees it straight away.", x, sy + size + 4f, w);
		return end(top, bodyH);
	}

	private float chestCard(CosmeticsApi.Mine mine, float x, float y, float w) {
		float top = y;
		if (!mine.chestGranted()) {
			y = card("chest", "Chest", "Not unlocked", x, y, w, 12f);
			note("Given out on stray.gay.", x, y, w);
			return end(top, 12f);
		}
		boolean hidden = currentBool("chest", "hidden", mine.chestHidden());
		float size = currentFloat("chest", "size", mine.chestSize());
		float lift = currentFloat("chest", "y", mine.chestY());
		float bodyH = 18f + 6f + 20f + 20f - 2f;
		y = card("chest", "Chest", hidden ? "Hidden" : "Showing", x, y, w, bodyH);
		chip(hidden ? "Show" : "Hide", x + CARD_PAD, y, false, () -> CosmeticsApi.change("chest", "hidden", new JsonPrimitive(!currentBool("chest", "hidden", mine.chestHidden()))));
		y += 18f + 6f;
		float sw = w - CARD_PAD * 2;
		y = slider("Size", Math.round(size * 100) + "%", (size - 0.5f) / 1.1f, x + CARD_PAD, y, sw,
			t -> CosmeticsApi.change("chest", "size", new JsonPrimitive(Math.round((0.5f + (float) t * 1.1f) * 100f) / 100f)));
		slider("Position", Math.abs(lift) < 0.05f ? "0" : String.format(java.util.Locale.ROOT, "%+.1f", -lift), (lift + 1.5f) / 3.5f, x + CARD_PAD, y, sw,
			t -> CosmeticsApi.change("chest", "y", new JsonPrimitive(Math.round((-1.5f + (float) t * 3.5f) * 20f) / 20f)));
		return end(top, bodyH);
	}

	// ------------------------------------------------------------------ values

	private static boolean currentBool(String part, String key, boolean server) {
		JsonElement e = CosmeticsApi.current(part, key);
		return e == null ? server : e.getAsBoolean();
	}

	private static float currentFloat(String part, String key, float server) {
		JsonElement e = CosmeticsApi.current(part, key);
		return e == null ? server : e.getAsFloat();
	}

	private static String currentText(String part, String key, String server) {
		JsonElement e = CosmeticsApi.current(part, key);
		return e == null ? server : e.getAsString();
	}

	private static String duration(int seconds) {
		if (seconds >= 3600) {
			return (seconds / 3600) + "h " + (seconds % 3600 / 60) + "m";
		}
		if (seconds >= 60) {
			return (seconds / 60) + "m";
		}
		return seconds + "s";
	}

	private void pickSkin() {
		if (pickingSkin) {
			return;
		}
		pickingSkin = true;
		focus = "skin";
		FilePicker.open("Select skin", "PNG skin", "png", path -> {
			pickingSkin = false;
			if (path == null) {
				return;
			}
			try {
				byte[] bytes = Files.readAllBytes(path);
				CosmeticsApi.Mine mine = CosmeticsApi.mine();
				CosmeticsApi.uploadSkin(bytes, mine != null && mine.skinSlim());
			} catch (Exception e) {
				// The status line says what went wrong on upload; a read error is rare.
			}
		}, error -> pickingSkin = false);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		if (MouseButtons.left(event) && GuiDraw.hovered(event.x(), event.y(), previewX, previewY, previewW, previewH)) {
			// The preview panel: start turning the model.
			draggingModel = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingModel) {
			targetYaw -= (float) dx * 2.2f;
			yaw = targetYaw;
			pitch = Mth.clamp(pitch + (float) dy * 0.8f, -30f, 40f);
			lastDragAt = Util.getMillis();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingModel) {
			draggingModel = false;
			lastDragAt = Util.getMillis();
		}
		return super.mouseReleased(event);
	}
}
