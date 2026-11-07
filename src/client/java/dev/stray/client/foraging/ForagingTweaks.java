package dev.stray.client.foraging;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.location.SkyblockLocation;
import dev.stray.client.visual.NickSteal;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foraging quality of life, after the SkyHanni features of the same names:
 * hides the floating blocks a Galatea tree throws off while it is cut,
 * folds the multi-line Sweep Details breakdown into one line, and folds the
 * Tree Gift reward box into one line plus its bonus drops.
 */
public final class ForagingTweaks {
	private static final Set<Block> TREE_BLOCKS = Set.of(
		Blocks.STRIPPED_SPRUCE_WOOD,
		Blocks.MANGROVE_WOOD,
		Blocks.MANGROVE_LEAVES,
		Blocks.AZALEA_LEAVES,
		Blocks.STRIPPED_BIRCH_WOOD,
		Blocks.STRIPPED_MANGROVE_WOOD,
		Blocks.OAK_LEAVES
	);
	/** Broken tree pieces drift level and shrink to between these scales. */
	private static final float MIN_SCALE = 0.4f;
	private static final float MAX_SCALE = 1.0f;
	private static final double LEVEL_EPSILON = 1e-6;

	private static final Pattern COLOR = Pattern.compile("§.");
	private static final Pattern SWEEP_HEADER = Pattern.compile("^Sweep Details: ([\\d,.]+)\\S? Sweep$");
	private static final Pattern SWEEP_TOUGHNESS = Pattern.compile("^(.+?) Tree Toughness: ([\\d,.]+) ([\\d,.]+) Logs$");
	private static final Pattern SWEEP_PENALTY = Pattern.compile("^(.+?): -([\\d,.]+)% Sweep ([\\d,.]+) Logs(?: (.+))?$");
	/** Color code right before the log count: green means the calculation is finished. */
	private static final Pattern LOGS_COLOR = Pattern.compile("(§[0-9a-f])(?:§[k-or])*[\\d,.]+ (?:§.)*Logs");

	private static final Pattern GIFT_HEADER = Pattern.compile("^TREE GIFT$");
	private static final Pattern GIFT_BONUS = Pattern.compile("^BONUS GIFT$");
	private static final Pattern GIFT_CUT = Pattern.compile("^You helped cut ([\\d.]+)% of the (.+) Tree\\.$");
	private static final Pattern GIFT_CUT_COLOR = Pattern.compile("(§[0-9a-f])(?:§[k-or])*[\\d.]+%");
	private static final Pattern GIFT_REWARDS = Pattern.compile("^\\+([\\d,]+) rewards gained!(?: \\(hover\\))?$");
	private static final Pattern GIFT_PHANTOM = Pattern.compile("^A (.+) fell from the Tree!$");
	private static final Pattern GIFT_DROP = Pattern.compile("^(.*?)\\s*(?:§.)*\\((?:§.)*([\\d.]+)%(?:§.)*\\)$");

	private static final long SWEEP_TIMEOUT_MS = 2_000L;
	private static final long GIFT_TIMEOUT_MS = 5_000L;

	// Sweep Details state
	private static boolean inSweep;
	private static long sweepAt;
	private static String sweepAmount = "";
	private static String treeType = "";
	private static String toughness = "";
	private static String initialLogs = "";
	private static String finalLogs = "";
	private static String proTip = "";
	private static final List<String> penalties = new ArrayList<>();

	// Tree Gift state
	private static Component heldSeparator;
	private static boolean inGift;
	private static boolean inBonus;
	private static long giftAt;
	private static String giftTree = "";
	private static String giftPercent = "";
	private static String giftRewards = "0";
	private static HoverEvent giftHover;
	private static final List<String> giftDrops = new ArrayList<>();

	private ForagingTweaks() {
	}

	// ---------------------------------------------------------------- tree blocks

	/**
	 * SkyHanni's {@code FORAGING_CUSTOM_TREES} islands. The tab list names Galatea "Moonglade Marsh",
	 * so matching "galatea" alone never turned this on.
	 */
	private static boolean customTreeIsland() {
		String area = SkyblockLocation.area.toLowerCase(Locale.ROOT);
		return area.contains("moonglade marsh") || area.contains("torrhus canyon") || area.contains("galatea");
	}

	/** True for the floating blocks a Galatea or Torrhus Canyon tree spawns while it is being cut. */
	public static boolean hideEntity(Entity entity) {
		if (!(entity instanceof Display.BlockDisplay display) || !StrayConfig.get().foragingHideTreeBlocks) {
			return false;
		}
		if (!SkyblockLocation.inSkyblock || !customTreeIsland()) {
			return false;
		}
		if (!TREE_BLOCKS.contains(display.getBlockState().getBlock())) {
			return false;
		}
		Vec3 look = display.getLookAngle();
		if (Math.abs(look.y) > LEVEL_EPSILON) {
			return false;
		}
		Display.RenderState state = display.renderState();
		if (state == null) {
			return false;
		}
		Transformation transformation = state.transformation().get(0f);
		if (transformation == null) {
			return false;
		}
		Vector3fc scale = transformation.scale();
		if (scale.x() != scale.y() || scale.x() != scale.z()) {
			return false;
		}
		return scale.x() >= MIN_SCALE && scale.x() <= MAX_SCALE;
	}

	// ---------------------------------------------------------------- chat

	/** Returns false when the message was folded into a compact one and should not show. */
	public static boolean allowChat(Component message, boolean overlay) {
		if (overlay || message == null || !SkyblockLocation.inSkyblock) {
			return true;
		}
		StrayConfig config = StrayConfig.get();
		String legacy = NickSteal.toLegacy(message).replace("§r", "");
		String plain = COLOR.matcher(legacy).replaceAll("").trim();
		long now = System.currentTimeMillis();
		boolean allow = true;
		if (config.foragingCompactTreeGift || heldSeparator != null || inGift) {
			allow = giftChat(message, legacy, plain, now, config.foragingCompactTreeGift);
		}
		if (allow && config.foragingCompactSweep) {
			allow = sweepChat(legacy, plain, now);
		} else if (!config.foragingCompactSweep && inSweep) {
			resetSweep();
		}
		return allow;
	}

	private static boolean sweepChat(String legacy, String plain, long now) {
		if (inSweep && now - sweepAt > SWEEP_TIMEOUT_MS) {
			flushSweep();
		}
		Matcher header = SWEEP_HEADER.matcher(plain);
		if (header.matches()) {
			if (inSweep) {
				flushSweep();
			}
			resetSweep();
			inSweep = true;
			sweepAt = now;
			sweepAmount = header.group(1);
			return false;
		}
		if (!inSweep) {
			return true;
		}
		Matcher tough = SWEEP_TOUGHNESS.matcher(plain);
		if (tough.matches()) {
			sweepAt = now;
			treeType = tough.group(1).trim();
			toughness = stripZero(tough.group(2));
			initialLogs = tough.group(3);
			finalLogs = initialLogs;
			if (finalLine(legacy)) {
				flushSweep();
			}
			return false;
		}
		Matcher penalty = SWEEP_PENALTY.matcher(plain);
		if (penalty.matches()) {
			sweepAt = now;
			finalLogs = penalty.group(3);
			penalties.add(penalty.group(1).trim() + "|" + penalty.group(2) + "|" + finalLogs);
			if (penalty.group(4) != null) {
				proTip = penalty.group(4).trim();
			}
			if (finalLine(legacy)) {
				flushSweep();
			}
			return false;
		}
		return true;
	}

	private static boolean finalLine(String legacy) {
		Matcher matcher = LOGS_COLOR.matcher(legacy);
		String color = "";
		while (matcher.find()) {
			color = matcher.group(1);
		}
		return color.equals("§a");
	}

	private static void flushSweep() {
		if (!inSweep) {
			return;
		}
		StringBuilder line = new StringBuilder("§2").append(sweepAmount).append("∮ Sweep");
		if (!initialLogs.isEmpty()) {
			line.append(" §7· §e").append(initialLogs).append(" logs");
		}
		for (String entry : penalties) {
			String[] parts = entry.split("\\|", 3);
			line.append(" §7(§c-").append(parts[1]).append("% §7").append(parts[0]).append(")");
		}
		line.append(" §7→ §a").append(finalLogs.isEmpty() ? "?" : finalLogs).append(" logs");
		if (!proTip.isEmpty()) {
			line.append("\n  §6Pro tip: §c").append(proTip);
		}

		StringBuilder hover = new StringBuilder("§6Initial §2Sweep§7: §2").append(sweepAmount);
		if (!initialLogs.isEmpty()) {
			hover.append("\n§6Initial Logs: §e").append(initialLogs);
			if (!treeType.isEmpty()) {
				hover.append(" §6").append(treeType).append(" Logs");
			}
			if (!toughness.isEmpty()) {
				hover.append(" §7(§6").append(toughness).append(" toughness§7)");
			}
		}
		for (String entry : penalties) {
			String[] parts = entry.split("\\|", 3);
			hover.append("\n§e").append(parts[0]).append("§7: §c-").append(parts[1]).append("% §7(").append(parts[2]).append(" logs)");
		}
		hover.append("\n§6Final Logs: §a").append(finalLogs.isEmpty() ? "?" : finalLogs);
		if (!treeType.isEmpty()) {
			hover.append(" §6").append(treeType).append(" Logs");
		}
		hover.append("\n\n§eClick to open the Tree Gifts guide!");

		MutableComponent out = Component.literal(line.toString()).withStyle(
			Style.EMPTY
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString())))
				.withClickEvent(new ClickEvent.RunCommand("/treegifts"))
		);
		resetSweep();
		tell(out);
	}

	private static void resetSweep() {
		inSweep = false;
		sweepAmount = "";
		treeType = "";
		toughness = "";
		initialLogs = "";
		finalLogs = "";
		proTip = "";
		penalties.clear();
	}

	private static boolean giftChat(Component message, String legacy, String plain, long now, boolean enabled) {
		if (inGift && now - giftAt > GIFT_TIMEOUT_MS) {
			flushGift();
		}
		boolean separator = isGiftSeparator(legacy, plain);
		if (heldSeparator != null) {
			Component held = heldSeparator;
			heldSeparator = null;
			if (enabled && GIFT_HEADER.matcher(plain).matches()) {
				startGift(now);
				return false;
			}
			// Not a Tree Gift after all: show the separator we held back.
			tell(held);
		}
		if (!enabled) {
			if (inGift) {
				flushGift();
			}
			return true;
		}
		if (separator) {
			if (inGift) {
				flushGift();
				return false;
			}
			heldSeparator = message;
			return false;
		}
		if (!inGift) {
			return true;
		}
		giftAt = now;
		if (GIFT_BONUS.matcher(plain).matches()) {
			inBonus = true;
			return false;
		}
		Matcher cut = GIFT_CUT.matcher(plain);
		if (cut.matches()) {
			Matcher color = GIFT_CUT_COLOR.matcher(legacy);
			giftPercent = (color.find() ? color.group(1) : "§a") + cut.group(1) + "%";
			giftTree = cut.group(2).trim();
			return false;
		}
		Matcher rewards = GIFT_REWARDS.matcher(plain);
		if (rewards.matches()) {
			giftRewards = rewards.group(1);
			giftHover = findHover(message);
			return false;
		}
		Matcher phantom = GIFT_PHANTOM.matcher(plain);
		if (phantom.matches()) {
			giftDrops.add("A wild §d" + phantom.group(1).trim() + " §fappeared!");
			return false;
		}
		if (inBonus) {
			Matcher drop = GIFT_DROP.matcher(dropCentering(legacy));
			if (drop.matches() && !COLOR.matcher(drop.group(1)).replaceAll("").isBlank()) {
				giftDrops.add(drop.group(1).trim() + " §8(" + drop.group(2) + "%)");
			}
		}
		return false;
	}

	private static boolean isGiftSeparator(String legacy, String plain) {
		if (plain.length() < 32 || !legacy.contains("§2")) {
			return false;
		}
		for (int i = 0; i < plain.length(); i++) {
			if (plain.charAt(i) != '▬') {
				return false;
			}
		}
		return true;
	}

	private static void startGift(long now) {
		inGift = true;
		inBonus = false;
		giftAt = now;
		giftTree = "";
		giftPercent = "";
		giftRewards = "0";
		giftHover = null;
		giftDrops.clear();
	}

	private static void flushGift() {
		if (!inGift) {
			return;
		}
		inGift = false;
		inBonus = false;
		String tree = giftTree.isEmpty() ? "" : giftTree + " ";
		String percent = giftPercent.isEmpty() ? "§a?" : giftPercent;
		Style style = Style.EMPTY;
		if (giftHover != null) {
			style = style.withHoverEvent(giftHover);
		}
		tell(Component.literal(
			"§9" + tree + "Tree Gift. §7You helped cut " + percent + " §7and gained §e" + giftRewards + " rewards§a!"
		).withStyle(style));
		for (String drop : giftDrops) {
			tell(Component.literal("§f - " + drop));
		}
		giftDrops.clear();
		giftHover = null;
	}

	private static HoverEvent findHover(Component message) {
		HoverEvent[] found = new HoverEvent[1];
		message.visit((style, text) -> {
			if (style.getHoverEvent() != null) {
				found[0] = style.getHoverEvent();
				return Optional.of(Boolean.TRUE);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return found[0];
	}

	/** Drops the centering spaces at the start of a line but keeps its color codes. */
	private static String dropCentering(String legacy) {
		int i = 0;
		StringBuilder codes = new StringBuilder();
		while (i < legacy.length()) {
			char c = legacy.charAt(i);
			if (c == '§' && i + 1 < legacy.length()) {
				codes.append(c).append(legacy.charAt(i + 1));
				i += 2;
			} else if (Character.isWhitespace(c)) {
				i++;
			} else {
				break;
			}
		}
		return (codes + legacy.substring(i)).trim();
	}

	private static String stripZero(String value) {
		return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
	}

	private static void tell(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(message);
		}
	}
}
