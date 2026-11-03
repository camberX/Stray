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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Cosmetics: a live 3D preview of your player on the left, and on the right the cosmetics you
 * have with their settings (Stray skin, cape, wings, chest). Every change shows in the preview
 * at once and is sent to stray.gay a moment later (see {@link CosmeticsApi}). The preview turns
 * to face whatever you are editing; drag it to look around.
 */
public final class CosmeticsScreen extends Screen {
	private static final String[] WING_STYLES = {"angel", "demon", "fairy", "phoenix", "butterfly"};
	private static final String[] WING_NAMES = {"Angel", "Demon", "Fairy", "Phoenix", "Butterfly"};
	private static final int[] WING_DEFAULTS = {0xF4F6FF, 0x2B1D2E, 0x9AE6FF, 0xFF7A1A, 0x3D98CB};
	private static final int[] SWATCHES = {
		0xF4F6FF, 0x2B1D2E, 0x9AE6FF, 0xFF7A1A, 0x3D98CB, 0xFF4F8B,
		0xB57BFF, 0x52E08A, 0xFFD34F, 0xE8414B, 0x1C1C1C, 0xC9CED6
	};
	private static final float PAD = 10f;
	private static final float ROW = 16f;

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private float winX;
	private float winY;
	private float winW;
	private float winH;
	private float listX;
	private float listY;
	private float listW;
	private float listH;
	private float scroll;
	private float contentH;
	/** Preview yaw: where it is, where it is turning to, and whether you are dragging it. */
	private float yaw = 25f;
	private float targetYaw = 25f;
	private float pitch = 6f;
	private boolean draggingModel;
	private long lastDragAt;
	private DoubleConsumer draggingSlider;
	private float sliderX;
	private float sliderW;
	private boolean pickingSkin;
	private String focus = "skin";

	public CosmeticsScreen(Screen parent) {
		super(Component.literal("Cosmetics"));
		this.parent = parent;
		CosmeticsApi.refresh();
	}

	private record Hit(float x, float y, float w, float h, Runnable click, DoubleConsumer slide) {
		boolean contains(double mx, double my) {
			return mx >= x && mx <= x + w && my >= y && my <= y + h;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.gui.setScreen(parent);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	private void layout() {
		winW = Math.min(470f, width - 20f);
		winH = Math.min(310f, height - 20f);
		winX = (width - winW) / 2f;
		winY = (height - winH) / 2f;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		hits.clear();
		layout();
		Font font = minecraft.font;
		GuiDraw.fill(graphics, 0, 0, width, height, 0xB4000000);
		ClickLook.outside(graphics, winX, winY, winW, winH, Theme.WINDOW_RADIUS, Theme.WINDOW, Theme.LINE);
		GuiDraw.title(graphics, font, "COSMETICS", winX + 12, winY + 8, Theme.TEXT);
		GuiDraw.small(graphics, font, "Only players with Stray see these", winX + 12 + GuiDraw.titleWidth(font, "COSMETICS") + 6, winY + 10, Theme.MUTED);
		closeButton(graphics, font, mouseX, mouseY);

		float previewW = Math.min(150f, winW * 0.34f);
		float top = winY + 28f;
		float bottom = winY + winH - 22f;
		drawPreview(graphics, font, mouseX, mouseY, winX + PAD, top, previewW, bottom - top, delta);

		listX = winX + PAD + previewW + PAD;
		listY = top;
		listW = winX + winW - PAD - listX;
		listH = bottom - top;
		boolean clip = GuiDraw.scissor(graphics, listX, listY, listW, listH);
		int mark = hits.size();
		float y = listY - scroll;
		CosmeticsApi.Mine mine = CosmeticsApi.mine();
		if (mine == null) {
			GuiDraw.menu(graphics, font, CosmeticsApi.loading() ? "Logging in to stray.gay…" : "Couldn't load your cosmetics.", listX + 4, y + 4, Theme.MUTED);
			if (!CosmeticsApi.loading()) {
				y = button(graphics, font, mouseX, mouseY, listX + 4, y + 18, 70, "Try again", CosmeticsApi::refresh) + 4;
			}
		} else {
			y = skinCard(graphics, font, mouseX, mouseY, mine, y);
			y = capeCard(graphics, font, mouseX, mouseY, mine, y);
			y = wingsCard(graphics, font, mouseX, mouseY, mine, y);
			y = chestCard(graphics, font, mouseX, mouseY, mine, y);
		}
		contentH = y + scroll - listY;
		// Clicks outside the visible list area don't count.
		for (int i = hits.size() - 1; i >= mark; i--) {
			Hit hit = hits.get(i);
			if (hit.y + hit.h < listY || hit.y > listY + listH) {
				hits.remove(i);
			}
		}
		if (clip) {
			GuiDraw.disableScissor(graphics);
		}
		String status = CosmeticsApi.status();
		if (!status.isBlank()) {
			GuiDraw.small(graphics, font, status, winX + 12, winY + winH - 15, Theme.MUTED);
		}
		yaw += Mth.wrapDegrees(targetYaw - yaw) * Math.min(1f, delta * 0.25f + 0.08f);
	}

	// ------------------------------------------------------------------ preview

	private void drawPreview(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, float h, float delta) {
		GuiDraw.rounded(graphics, x, y, w, h, 6, Theme.CARD);
		hits.add(new Hit(x, y, w, h, null, null));
		LocalPlayer player = minecraft.player;
		if (player == null) {
			GuiDraw.small(graphics, font, "Join a world", x + 8, y + h / 2f - 8, Theme.MUTED);
			GuiDraw.small(graphics, font, "to see the preview", x + 8, y + h / 2f + 2, Theme.MUTED);
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
		GuiDraw.small(graphics, font, "Drag to turn", x + (w - GuiDraw.smallWidth(font, "Drag to turn")) / 2f, y + h - 12, Theme.MUTED);
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

	private float card(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, String id, String title, String note, float y, float bodyH) {
		float h = 22f + bodyH;
		boolean hot = GuiDraw.hovered(mouseX, mouseY, listX, y, listW, h) && GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH);
		if (hot && !draggingModel && draggingSlider == null) {
			focus = id;
		}
		GuiDraw.rounded(graphics, listX, y, listW, h, 6, focus.equals(id) ? Theme.CARD_HOVER : Theme.CARD);
		GuiDraw.menu(graphics, font, title, listX + 8, y + 6, Theme.TEXT);
		if (note != null && !note.isBlank()) {
			float nw = GuiDraw.smallWidth(font, note);
			GuiDraw.small(graphics, font, note, listX + listW - 8 - nw, y + 7, Theme.MUTED);
		}
		return y + 20f;
	}

	private float skinCard(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, CosmeticsApi.Mine mine, float y) {
		boolean slim = currentBool("skin", "slim", mine.skinSlim());
		String note = mine.skinHas() ? (slim ? "Stray skin · slim arms" : "Stray skin · classic arms") : "Your Minecraft skin";
		float top = y;
		y = card(graphics, font, mouseX, mouseY, "skin", "Skin", note, y, ROW * 2 + 8);
		float x = listX + 8;
		x = buttonX(graphics, font, mouseX, mouseY, x, y, pickingSkin ? "Choosing…" : "Choose PNG…", this::pickSkin) + 4;
		if (mine.skinHas()) {
			x = toggleChip(graphics, font, mouseX, mouseY, x, y, "Classic", !slim, () -> CosmeticsApi.change("skin", "slim", new JsonPrimitive(false))) + 2;
			x = toggleChip(graphics, font, mouseX, mouseY, x, y, "Slim", slim, () -> CosmeticsApi.change("skin", "slim", new JsonPrimitive(true))) + 4;
			buttonX(graphics, font, mouseX, mouseY, x, y, "Remove", CosmeticsApi::removeSkin);
		}
		String limit = mine.skinRetry() > 0 ? "Next upload in " + mine.skinRetry() + "s" : mine.skinLeft() + " uploads left today";
		GuiDraw.small(graphics, font, "64x64 PNG. Your real Minecraft skin stays the same. " + limit + ".", listX + 8, y + ROW + 4, Theme.MUTED);
		return top + 22f + ROW * 2 + 8 + 6f;
	}

	private float capeCard(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, CosmeticsApi.Mine mine, float y) {
		float top = y;
		if (!mine.whitelisted()) {
			y = card(graphics, font, mouseX, mouseY, "cape", "Cape", "Not on the cape list", y, ROW);
			GuiDraw.small(graphics, font, "Capes are given out on stray.gay.", listX + 8, y + 3, Theme.MUTED);
			return top + 22f + ROW + 6f;
		}
		String note = mine.hasCape() ? "Live" : "No cape yet";
		if (mine.capeRetry() > 0) {
			note += " · change in " + duration(mine.capeRetry());
		}
		y = card(graphics, font, mouseX, mouseY, "cape", "Cape", note, y, ROW * 2 + 8);
		float x = listX + 8;
		x = buttonX(graphics, font, mouseX, mouseY, x, y, "Cape creator…", CustomCape::pickCreate) + 4;
		if (CustomCape.ready()) {
			x = buttonX(graphics, font, mouseX, mouseY, x, y, "Publish", () -> {
				ShopCape.publish(CustomCape.png());
				Util.nonCriticalIoPool().execute(() -> {
					try {
						Thread.sleep(1500L);
					} catch (InterruptedException ignored) {
					}
					CosmeticsApi.refresh();
				});
			}) + 4;
		}
		if (mine.hasCape()) {
			buttonX(graphics, font, mouseX, mouseY, x, y, "Remove", () -> {
				ShopCape.unpublish();
				CosmeticsApi.refresh();
			});
		}
		String status = ShopCape.publishStatus();
		GuiDraw.small(graphics, font, status.isBlank() ? "Make a cape from any image, then publish it." : status, listX + 8, y + ROW + 4, Theme.MUTED);
		return top + 22f + ROW * 2 + 8 + 6f;
	}

	private float wingsCard(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, CosmeticsApi.Mine mine, float y) {
		float top = y;
		if (!mine.wingsGranted()) {
			y = card(graphics, font, mouseX, mouseY, "wings", "Wings", "Not unlocked", y, ROW);
			GuiDraw.small(graphics, font, "Wings are given out on stray.gay.", listX + 8, y + 3, Theme.MUTED);
			return top + 22f + ROW + 6f;
		}
		boolean hidden = currentBool("wings", "hidden", mine.wingsHidden());
		String style = currentText("wings", "style", mine.wingsStyle());
		String rgb = currentText("wings", "rgb", mine.wingsRgb());
		y = card(graphics, font, mouseX, mouseY, "wings", "Wings", hidden ? "Hidden" : "Showing", y, ROW * 3 + 12);
		float x = listX + 8;
		x = toggleChip(graphics, font, mouseX, mouseY, x, y, hidden ? "Show" : "Hide", false, () -> CosmeticsApi.change("wings", "hidden", new JsonPrimitive(!currentBool("wings", "hidden", mine.wingsHidden())))) + 8;
		for (int i = 0; i < WING_STYLES.length; i++) {
			String id = WING_STYLES[i];
			String defaultRgb = String.format("%06X", WING_DEFAULTS[i]);
			x = toggleChip(graphics, font, mouseX, mouseY, x, y, WING_NAMES[i], id.equals(style), () -> {
				// A new style starts in its own color, like on the desk; pick a swatch after.
				CosmeticsApi.change("wings", "style", new JsonPrimitive(id));
				CosmeticsApi.change("wings", "rgb", new JsonPrimitive(defaultRgb));
			}) + 2;
		}
		float sy = y + ROW + 4;
		float sx = listX + 8;
		float size = 12f;
		for (int color : SWATCHES) {
			String hex = String.format("%06X", color);
			boolean on = hex.equalsIgnoreCase(rgb);
			boolean hot = GuiDraw.hovered(mouseX, mouseY, sx, sy, size, size);
			GuiDraw.rounded(graphics, sx - 1, sy - 1, size + 2, size + 2, 3, on ? Theme.ACCENT : hot ? Theme.TEXT : Theme.LINE);
			GuiDraw.rounded(graphics, sx, sy, size, size, 2, 0xFF000000 | color);
			hits.add(new Hit(sx, sy, size, size, () -> CosmeticsApi.change("wings", "rgb", new JsonPrimitive(hex)), null));
			sx += size + 4;
			if (sx + size > listX + listW - 8) {
				break;
			}
		}
		GuiDraw.small(graphics, font, "Pick a style and a color. Everyone with Stray sees it straight away.", listX + 8, y + ROW * 2 + 10, Theme.MUTED);
		return top + 22f + ROW * 3 + 12 + 6f;
	}

	private float chestCard(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, CosmeticsApi.Mine mine, float y) {
		float top = y;
		if (!mine.chestGranted()) {
			y = card(graphics, font, mouseX, mouseY, "chest", "Chest", "Not unlocked", y, ROW);
			GuiDraw.small(graphics, font, "Given out on stray.gay.", listX + 8, y + 3, Theme.MUTED);
			return top + 22f + ROW + 6f;
		}
		boolean hidden = currentBool("chest", "hidden", mine.chestHidden());
		float size = currentFloat("chest", "size", mine.chestSize());
		float lift = currentFloat("chest", "y", mine.chestY());
		y = card(graphics, font, mouseX, mouseY, "chest", "Chest", hidden ? "Hidden" : "Showing", y, ROW * 3 + 8);
		toggleChip(graphics, font, mouseX, mouseY, listX + 8, y, hidden ? "Show" : "Hide", false, () -> CosmeticsApi.change("chest", "hidden", new JsonPrimitive(!currentBool("chest", "hidden", mine.chestHidden()))));
		y += ROW + 4;
		y = slider(graphics, font, mouseX, mouseY, y, "Size", Math.round(size * 100) + "%", (size - 0.5f) / 1.1f,
			t -> CosmeticsApi.change("chest", "size", new JsonPrimitive(Math.round((0.5f + (float) t * 1.1f) * 100f) / 100f)));
		slider(graphics, font, mouseX, mouseY, y, "Position", Math.abs(lift) < 0.05f ? "0" : String.format(java.util.Locale.ROOT, "%+.1f", -lift), (lift + 1.5f) / 3.5f,
			t -> CosmeticsApi.change("chest", "y", new JsonPrimitive(Math.round((-1.5f + (float) t * 3.5f) * 20f) / 20f)));
		return top + 22f + ROW * 3 + 8 + 6f;
	}

	// ------------------------------------------------------------------ widgets

	private float slider(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float y, String label, String value, float t, DoubleConsumer set) {
		float x = listX + 8;
		float labelW = 52f;
		float valueW = 34f;
		float trackX = x + labelW;
		float trackW = listW - 16 - labelW - valueW;
		GuiDraw.menu(graphics, font, label, x, y + 3, Theme.TEXT);
		float cy = y + ROW / 2f;
		GuiDraw.rounded(graphics, trackX, cy - 2, trackW, 4, 2, Theme.TRACK);
		float k = Mth.clamp(t, 0f, 1f);
		GuiDraw.rounded(graphics, trackX, cy - 2, trackW * k, 4, 2, Theme.ACCENT);
		GuiDraw.rounded(graphics, trackX + trackW * k - 4, cy - 4, 8, 8, 4, Theme.TEXT);
		GuiDraw.menu(graphics, font, value, trackX + trackW + 6, y + 3, Theme.MUTED);
		hits.add(new Hit(trackX - 4, y, trackW + 8, ROW, null, set));
		if (draggingSlider == set) {
			sliderX = trackX;
			sliderW = trackW;
		}
		return y + ROW + 2;
	}

	private float button(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, float w, String label, Runnable click) {
		boolean hot = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW - 2);
		GuiDraw.rounded(graphics, x, y, w, ROW - 2, 4, hot ? Theme.ACCENT_DIM : Theme.TRACK);
		GuiDraw.menu(graphics, font, label, x + (w - GuiDraw.menuWidth(font, label)) / 2f, y + 3, Theme.TEXT);
		hits.add(new Hit(x, y, w, ROW - 2, click, null));
		return y + ROW;
	}

	private float buttonX(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, String label, Runnable click) {
		float w = GuiDraw.menuWidth(font, label) + 14f;
		button(graphics, font, mouseX, mouseY, x, y, w, label, click);
		return x + w;
	}

	private float toggleChip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, float x, float y, String label, boolean on, Runnable click) {
		float w = GuiDraw.menuWidth(font, label) + 12f;
		boolean hot = GuiDraw.hovered(mouseX, mouseY, x, y, w, ROW - 2);
		GuiDraw.rounded(graphics, x, y, w, ROW - 2, 7, on ? Theme.ACCENT : hot ? Theme.ACCENT_DIM : Theme.TRACK);
		GuiDraw.menu(graphics, font, label, x + 6, y + 3, on ? 0xFF0B0E14 : Theme.TEXT);
		hits.add(new Hit(x, y, w, ROW - 2, click, null));
		return x + w;
	}

	private void closeButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		float s = 14f;
		float x = winX + winW - s - 8;
		float y = winY + 7;
		boolean hot = GuiDraw.hovered(mouseX, mouseY, x, y, s, s);
		GuiDraw.rounded(graphics, x, y, s, s, 4, hot ? 0xFFE8414B : Theme.TRACK);
		GuiDraw.menu(graphics, font, "×", x + (s - GuiDraw.menuWidth(font, "×")) / 2f, y + 3, Theme.TEXT);
		hits.add(new Hit(x, y, s, s, this::onClose, null));
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
		if (!MouseButtons.left(event)) {
			return super.mouseClicked(event, doubled);
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hit = hits.get(i);
			if (!hit.contains(event.x(), event.y())) {
				continue;
			}
			if (hit.slide != null) {
				draggingSlider = hit.slide;
				sliderX = hit.x + 4;
				sliderW = hit.w - 8;
				hit.slide.accept(Mth.clamp((event.x() - sliderX) / sliderW, 0, 1));
				return true;
			}
			if (hit.click != null) {
				hit.click.run();
				return true;
			}
			// The preview panel: start turning the model.
			draggingModel = true;
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingSlider != null) {
			draggingSlider.accept(Mth.clamp((event.x() - sliderX) / Math.max(1f, sliderW), 0, 1));
			return true;
		}
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
		draggingSlider = null;
		if (draggingModel) {
			draggingModel = false;
			lastDragAt = Util.getMillis();
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && GuiDraw.hovered(mouseX, mouseY, listX, listY, listW, listH)) {
			scroll = Mth.clamp(scroll - (float) scrollY * 18f, 0f, Math.max(0f, contentH - listH));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}
}
