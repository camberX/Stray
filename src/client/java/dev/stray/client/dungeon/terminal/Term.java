package dev.stray.client.dungeon.terminal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One F7 terminal type: how to tell it from the window title and which slots still
 * need clicking. Solving follows NoammAddons' TerminalSolver (CC0) one to one; only
 * the drawing is Stray's own (see {@link TerminalGui}).
 */
public abstract class Term {
	/** A slot to click; {@code btn} 0 = middle click, 1 = right click (Rubix: signed steps left). */
	public record Click(int slot, int btn) {
	}

	public final String name;
	final Pattern title;
	public final int slotCount;
	public final int gridW;
	public final int gridH;
	public final List<Click> solution = new ArrayList<>();
	private int total = -1;

	Term(String name, String title, int slotCount, int gridW, int gridH) {
		this.name = name;
		this.title = Pattern.compile(title);
		this.slotCount = slotCount;
		this.gridW = gridW;
		this.gridH = gridH;
	}

	protected abstract boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item);

	boolean trackProgress() {
		return true;
	}

	void onSlotClick(int slot) {
	}

	final boolean solve(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
		solution.clear();
		if (!update(items, title, slot, item)) {
			return false;
		}
		if (trackProgress() && total == -1) {
			total = remaining();
		}
		return true;
	}

	Click clickFor(int slot) {
		for (Click click : solution) {
			if (click.slot() == slot) {
				return click;
			}
		}
		return null;
	}

	void predict(Click click) {
		solution.remove(click);
	}

	void reset() {
		solution.clear();
		total = -1;
	}

	int remaining() {
		return solution.size();
	}

	public int max() {
		return total > 0 ? total : -1;
	}

	public int done() {
		return total == -1 ? 0 : total - remaining();
	}

	/** First column of the grid inside the 9-wide chest, the same for every terminal. */
	public int startCol() {
		return (9 - gridW) / 2;
	}

	public int startRow() {
		return 1;
	}

	static boolean pane(ItemStack stack, DyeColor color) {
		return stack != null && stack.is(Items.STAINED_GLASS_PANE.pick(color));
	}

	// ---------------------------------------------------------------- the six terminals

	static final Numbers NUMBERS = new Numbers();
	static final RedGreen RED_GREEN = new RedGreen();
	static final Colors COLORS = new Colors();
	static final StartWith START_WITH = new StartWith();
	static final Rubix RUBIX = new Rubix();
	static final Melody MELODY = new Melody();
	static final Term[] ALL = {NUMBERS, RED_GREEN, COLORS, START_WITH, RUBIX, MELODY};

	static Term fromTitle(String title) {
		for (Term term : ALL) {
			if (term.title.matcher(title).matches()) {
				return term;
			}
		}
		return null;
	}

	/** Click the red panes from the lowest count up. */
	static final class Numbers extends Term {
		final Map<Integer, Integer> counts = new java.util.HashMap<>();

		Numbers() {
			super("Numbers", "^Click in order!$", 36, 7, 2);
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			List<Map.Entry<Integer, ItemStack>> red = new ArrayList<>();
			for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(items).entrySet()) {
				if (pane(e.getValue(), DyeColor.RED)) {
					red.add(e);
				}
			}
			red.sort(java.util.Comparator.comparingInt(e -> e.getValue().getCount()));
			for (Map.Entry<Integer, ItemStack> e : red) {
				counts.put(e.getKey(), e.getValue().getCount());
				solution.add(new Click(e.getKey(), 0));
			}
			return true;
		}

		@Override
		Click clickFor(int slot) {
			return !solution.isEmpty() && solution.get(0).slot() == slot ? solution.get(0) : null;
		}

		@Override
		void reset() {
			super.reset();
			counts.clear();
		}
	}

	/** Every red pane. */
	static final class RedGreen extends Term {
		RedGreen() {
			super("Red Green", "^Correct all the panes!$", 45, 5, 3);
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(items).entrySet()) {
				if (pane(e.getValue(), DyeColor.RED)) {
					solution.add(new Click(e.getKey(), 0));
				}
			}
			return true;
		}
	}

	/** Every item of the colour in the title that is not lit up yet. */
	static final class Colors extends Term {
		Colors() {
			super("Colors", "^Select all the ([\\w ]+) items!$", 54, 7, 4);
		}

		private static Set<String> prefixes(DyeColor color) {
			return switch (color) {
				case BLACK -> Set.of("black", "ink");
				case BLUE -> Set.of("blue", "lapis");
				case BROWN -> Set.of("brown", "cocoa");
				case WHITE -> Set.of("white", "bone", "wool");
				case GREEN -> Set.of("green", "cactus");
				case RED -> Set.of("red", "rose");
				case YELLOW -> Set.of("yellow", "dandelion");
				case LIGHT_GRAY -> Set.of("silver", "light gray");
				default -> Set.of(color.name().toLowerCase(Locale.ROOT).replace('_', ' '));
			};
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			Matcher m = this.title.matcher(title);
			if (!m.matches()) {
				return false;
			}
			String wanted = m.group(1).replace("SILVER", "LIGHT GRAY");
			DyeColor color = null;
			for (DyeColor c : DyeColor.values()) {
				if (c.name().replace('_', ' ').equalsIgnoreCase(wanted)) {
					color = c;
				}
			}
			if (color == null) {
				return false;
			}
			Set<String> ok = prefixes(color);
			for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(items).entrySet()) {
				ItemStack stack = e.getValue();
				if (stack.isEmpty() || pane(stack, DyeColor.BLACK) || stack.hasFoil()) {
					continue;
				}
				String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
				for (String prefix : ok) {
					if (name.startsWith(prefix)) {
						solution.add(new Click(e.getKey(), 0));
						break;
					}
				}
			}
			return true;
		}
	}

	/** Every item whose name starts with the letter, skipping ones already clicked. */
	static final class StartWith extends Term {
		private Set<Item> special;
		final Set<Integer> solved = new HashSet<>();
		final Set<Integer> clicked = new HashSet<>();

		StartWith() {
			super("Starts With", "^What starts with: '(\\w)'\\?$", 45, 7, 3);
		}

		private Set<Item> special() {
			if (special == null) {
				special = new HashSet<>();
				for (Item item : BuiltInRegistries.ITEM) {
					if (item.components().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
						special.add(item);
					}
				}
				special.add(Items.GOLDEN_APPLE);
			}
			return special;
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			Matcher m = this.title.matcher(title);
			if (!m.matches()) {
				return false;
			}
			String letter = m.group(1).toLowerCase(Locale.ROOT);
			if (clicked.remove(slot)) {
				solved.add(slot);
			}
			for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(items).entrySet()) {
				ItemStack stack = e.getValue();
				if (stack.isEmpty() || !stack.getHoverName().getString().toLowerCase(Locale.ROOT).startsWith(letter)) {
					continue;
				}
				if (solved.contains(e.getKey())) {
					continue;
				}
				if (!stack.hasFoil() || special().contains(stack.getItem())) {
					solution.add(new Click(e.getKey(), 0));
				}
			}
			return true;
		}

		@Override
		void onSlotClick(int slot) {
			clicked.add(slot);
		}

		@Override
		Click clickFor(int slot) {
			return solved.contains(slot) ? null : super.clickFor(slot);
		}

		@Override
		void reset() {
			super.reset();
			solved.clear();
			clicked.clear();
		}
	}

	/** Turn every pane of the 3x3 to the colour that takes the fewest clicks. */
	static final class Rubix extends Term {
		static final DyeColor[] ORDER = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.GREEN, DyeColor.BLUE};
		static final int[] SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

		Rubix() {
			super("Rubix", "^Change all to same color!$", 45, 3, 3);
		}

		static int index(ItemStack stack) {
			for (int i = 0; i < ORDER.length; i++) {
				if (pane(stack, ORDER[i])) {
					return i;
				}
			}
			return -1;
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			int[] costs = new int[5];
			for (int i = 0; i < 5; i++) {
				for (int s : SLOTS) {
					int at = index(items.get(s));
					if (at != -1) {
						int dist = Math.abs(i - at);
						costs[i] += dist > 2 ? 5 - dist : dist;
					}
				}
			}
			int origin = 0;
			for (int i = 1; i < 5; i++) {
				if (costs[i] < costs[origin]) {
					origin = i;
				}
			}
			for (int s : SLOTS) {
				int at = index(items.get(s));
				if (at != -1 && at != origin) {
					int diff = origin - at;
					if (diff > 2) {
						diff -= 5;
					}
					if (diff < -2) {
						diff += 5;
					}
					solution.add(new Click(s, diff));
				}
			}
			return true;
		}

		@Override
		int remaining() {
			int sum = 0;
			for (Click click : solution) {
				sum += Math.abs(click.btn());
			}
			return sum;
		}

		@Override
		Click clickFor(int slot) {
			Click step = super.clickFor(slot);
			return step == null ? null : new Click(slot, step.btn() > 0 ? 0 : 1);
		}

		@Override
		void predict(Click click) {
			Click step = super.clickFor(click.slot());
			if (step == null) {
				return;
			}
			int next = step.btn() + (click.btn() == 0 ? -1 : 1);
			int at = solution.indexOf(step);
			if (next == 0) {
				solution.remove(at);
			} else {
				solution.set(at, new Click(click.slot(), next));
			}
		}
	}

	/** Click the row's button while the moving pane is in the marked column. */
	static final class Melody extends Term {
		static final int[] CLAY = {16, 25, 34, 43};
		/** Button rows: 3 (Noamm's layout) or 4 when the window has a fourth button (slot 43). */
		int rows = 3;
		int buttonRow = -1;
		int current = -1;
		int correct = -1;

		Melody() {
			super("Melody", "^Click the button on time!$", 54, 6, 3);
		}

		@Override
		boolean trackProgress() {
			return false;
		}

		@Override
		protected boolean update(Map<Integer, ItemStack> items, String title, int slot, ItemStack item) {
			ItemStack fourth = items.get(43);
			rows = fourth != null && fourth.is(Items.DYED_TERRACOTTA.pick(DyeColor.LIME)) || fourth != null && fourth.is(Items.DYED_TERRACOTTA.pick(DyeColor.RED)) ? 4 : 3;
			if (!pane(item, DyeColor.LIME)) {
				return true;
			}
			int newCorrect = -1;
			for (Map.Entry<Integer, ItemStack> e : new TreeMap<>(items).entrySet()) {
				if (pane(e.getValue(), DyeColor.MAGENTA)) {
					newCorrect = e.getKey() - 1;
					break;
				}
			}
			buttonRow = slot / 9 - 1;
			current = slot % 9 - 1;
			if (newCorrect != -1) {
				correct = newCorrect;
			}
			return true;
		}

		/** Melody has no solution list: the "slot" is the button row (0..rows-1). */
		@Override
		Click clickFor(int row) {
			return row >= 0 && row < rows ? new Click(CLAY[row], 0) : null;
		}

		@Override
		public int max() {
			return rows;
		}

		@Override
		public int done() {
			return Math.max(0, buttonRow);
		}

		@Override
		void reset() {
			super.reset();
			rows = 3;
			buttonRow = -1;
			current = -1;
			correct = -1;
		}
	}
}
