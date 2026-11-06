package dev.stray.client.dungeon.terminal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Offline copy of a terminal for /stray debug dev terminal: makes a random board the
 * way Hypixel lays it out and reacts to clicks like the server does (after a short
 * delay), so the custom GUI can be tried without a dungeon. Follows NoammAddons' sims.
 */
abstract class TerminalSim {
	final Term term;
	final Map<Integer, ItemStack> items = new HashMap<>();
	String title = "";

	TerminalSim(Term term) {
		this.term = term;
	}

	static TerminalSim of(String name) {
		String key = name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
		return switch (key) {
			case "numbers", "order" -> new NumbersSim();
			case "redgreen", "panes" -> new RedGreenSim();
			case "colors", "colours", "color" -> new ColorsSim();
			case "startswith", "startwith", "letter" -> new StartWithSim();
			case "rubix", "rubik", "rubiks" -> new RubixSim();
			case "melody" -> new MelodySim();
			case "random" -> random();
			default -> null;
		};
	}

	static TerminalSim random() {
		return switch (ThreadLocalRandom.current().nextInt(6)) {
			case 0 -> new NumbersSim();
			case 1 -> new RedGreenSim();
			case 2 -> new ColorsSim();
			case 3 -> new StartWithSim();
			case 4 -> new RubixSim();
			default -> new MelodySim();
		};
	}

	static final String[] NAMES = {"numbers", "redgreen", "colors", "startswith", "rubix", "melody", "random"};

	List<Integer> gridSlots() {
		List<Integer> slots = new ArrayList<>();
		int start = (9 - term.gridW) / 2;
		for (int row = 0; row < term.gridH; row++) {
			for (int col = 0; col < term.gridW; col++) {
				slots.add((row + 1) * 9 + start + col);
			}
		}
		return slots;
	}

	final void generate() {
		items.clear();
		// The rest of the chest is black glass, like the real one.
		for (int i = 0; i < term.slotCount; i++) {
			items.put(i, new ItemStack(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK)));
		}
		title = setup();
	}

	abstract String setup();

	abstract void click(Term.Click click);

	abstract boolean solved();

	void tick() {
	}

	static ItemStack pane(DyeColor color) {
		return new ItemStack(Items.STAINED_GLASS_PANE.pick(color));
	}

	static ItemStack lit(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return copy;
	}

	static final class NumbersSim extends TerminalSim {
		private int next = 1;

		NumbersSim() {
			super(Term.NUMBERS);
		}

		@Override
		String setup() {
			next = 1;
			List<Integer> slots = gridSlots();
			List<Integer> counts = new ArrayList<>();
			for (int i = 1; i <= slots.size(); i++) {
				counts.add(i);
			}
			Collections.shuffle(counts);
			for (int i = 0; i < slots.size(); i++) {
				ItemStack stack = pane(DyeColor.RED);
				stack.setCount(counts.get(i));
				items.put(slots.get(i), stack);
			}
			return "Click in order!";
		}

		@Override
		void click(Term.Click click) {
			ItemStack stack = items.get(click.slot());
			if (stack == null || !Term.pane(stack, DyeColor.RED) || stack.getCount() != next) {
				return;
			}
			next++;
			ItemStack done = pane(DyeColor.LIME);
			done.setCount(stack.getCount());
			items.put(click.slot(), done);
		}

		@Override
		boolean solved() {
			return next > gridSlots().size();
		}
	}

	static final class RedGreenSim extends TerminalSim {
		RedGreenSim() {
			super(Term.RED_GREEN);
		}

		@Override
		String setup() {
			for (int slot : gridSlots()) {
				items.put(slot, pane(Math.random() > 0.65 ? DyeColor.LIME : DyeColor.RED));
			}
			return "Correct all the panes!";
		}

		@Override
		void click(Term.Click click) {
			if (Term.pane(items.get(click.slot()), DyeColor.RED)) {
				items.put(click.slot(), pane(DyeColor.LIME));
			}
		}

		@Override
		boolean solved() {
			for (int slot : gridSlots()) {
				if (Term.pane(items.get(slot), DyeColor.RED)) {
					return false;
				}
			}
			return true;
		}
	}

	static final class ColorsSim extends TerminalSim {
		private static final String[] TYPES = {"wool", "stained_glass", "dye", "concrete"};
		private final Map<Integer, DyeColor> colors = new HashMap<>();
		private DyeColor target = DyeColor.WHITE;

		ColorsSim() {
			super(Term.COLORS);
		}

		@Override
		String setup() {
			colors.clear();
			ThreadLocalRandom random = ThreadLocalRandom.current();
			DyeColor[] all = DyeColor.values();
			target = all[random.nextInt(all.length)];
			List<Integer> slots = gridSlots();
			int first = slots.get(random.nextInt(slots.size()));
			for (int slot : slots) {
				// Wrong colours too, so it is not just "click everything".
				DyeColor color = slot == first || Math.random() > 0.65 ? target : all[random.nextInt(all.length)];
				colors.put(slot, color);
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(color.getSerializedName() + "_" + TYPES[random.nextInt(TYPES.length)]));
				items.put(slot, new ItemStack(item));
			}
			String name = target == DyeColor.LIGHT_GRAY ? "SILVER" : target.name().replace('_', ' ');
			return "Select all the " + name + " items!";
		}

		@Override
		void click(Term.Click click) {
			ItemStack stack = items.get(click.slot());
			if (stack == null || colors.get(click.slot()) != target || stack.hasFoil()) {
				return;
			}
			items.put(click.slot(), lit(stack));
		}

		@Override
		boolean solved() {
			for (Map.Entry<Integer, DyeColor> e : colors.entrySet()) {
				ItemStack stack = items.get(e.getKey());
				if (e.getValue() == target && (stack == null || !stack.hasFoil())) {
					return false;
				}
			}
			return true;
		}
	}

	static final class StartWithSim extends TerminalSim {
		private static Map<Character, List<Item>> byLetter;
		private char letter = 'a';

		StartWithSim() {
			super(Term.START_WITH);
		}

		private static Map<Character, List<Item>> byLetter() {
			if (byLetter == null) {
				byLetter = new HashMap<>();
				for (Item item : BuiltInRegistries.ITEM) {
					if (item == Items.AIR || item == Items.GOLDEN_APPLE || item.components().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
						continue;
					}
					String name = new ItemStack(item).getHoverName().getString();
					if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
						continue;
					}
					byLetter.computeIfAbsent(Character.toLowerCase(name.charAt(0)), k -> new ArrayList<>()).add(item);
				}
				byLetter.values().removeIf(list -> list.size() < 2);
			}
			return byLetter;
		}

		@Override
		String setup() {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			List<Character> letters = new ArrayList<>(byLetter().keySet());
			letter = letters.get(random.nextInt(letters.size()));
			List<Item> matching = byLetter().get(letter);
			List<Character> others = new ArrayList<>(letters);
			others.remove((Character) letter);
			List<Integer> slots = gridSlots();
			int first = slots.get(random.nextInt(slots.size()));
			for (int slot : slots) {
				List<Item> pool = slot == first || Math.random() > 0.65 ? matching : byLetter().get(others.get(random.nextInt(others.size())));
				items.put(slot, new ItemStack(pool.get(random.nextInt(pool.size()))));
			}
			return "What starts with: '" + Character.toUpperCase(letter) + "'?";
		}

		private boolean target(ItemStack stack) {
			return stack != null && !stack.hasFoil() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).startsWith(String.valueOf(letter));
		}

		@Override
		void click(Term.Click click) {
			ItemStack stack = items.get(click.slot());
			if (target(stack)) {
				items.put(click.slot(), lit(stack));
			}
		}

		@Override
		boolean solved() {
			for (int slot : gridSlots()) {
				if (target(items.get(slot))) {
					return false;
				}
			}
			return true;
		}
	}

	static final class RubixSim extends TerminalSim {
		RubixSim() {
			super(Term.RUBIX);
		}

		@Override
		String setup() {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			do {
				for (int slot : gridSlots()) {
					items.put(slot, pane(Term.Rubix.ORDER[random.nextInt(5)]));
				}
			} while (solved());
			return "Change all to same color!";
		}

		@Override
		void click(Term.Click click) {
			int at = Term.Rubix.index(items.get(click.slot()));
			if (at == -1) {
				return;
			}
			int change = click.btn() == 1 ? -1 : 1;
			items.put(click.slot(), pane(Term.Rubix.ORDER[Math.floorMod(at + change, 5)]));
		}

		@Override
		boolean solved() {
			int first = -2;
			for (int slot : gridSlots()) {
				int at = Term.Rubix.index(items.get(slot));
				if (first == -2) {
					first = at;
				} else if (at != first) {
					return false;
				}
			}
			return true;
		}
	}

	static final class MelodySim extends TerminalSim {
		private static final int MOVE = 10;
		private static final int BUTTON = 7;
		private int row = 1;
		private int correct = 1;
		private int current = 1;
		private int direction = 1;
		private int ticks;

		MelodySim() {
			super(Term.MELODY);
		}

		@Override
		String setup() {
			row = 1;
			correct = 1 + ThreadLocalRandom.current().nextInt(5);
			current = 1;
			direction = 1;
			ticks = 0;
			for (int r = 0; r <= 4; r++) {
				row(r);
			}
			return "Click the button on time!";
		}

		@Override
		void tick() {
			if (solved() || ++ticks < MOVE) {
				return;
			}
			ticks = 0;
			current += direction;
			if (current == 1 || current == 5) {
				direction = -direction;
			}
			row(row);
		}

		@Override
		void click(Term.Click click) {
			if (click.slot() != row * 9 + BUTTON) {
				return;
			}
			if (current != correct) {
				ticks = -MOVE;
				return;
			}
			correct = 1 + ThreadLocalRandom.current().nextInt(5);
			ticks = -MOVE / 3;
			row++;
			row(0);
			row(row - 1);
			row(row);
			row(4);
		}

		@Override
		boolean solved() {
			return row > 3;
		}

		private void row(int r) {
			if (r > 4) {
				return;
			}
			for (int col = 0; col <= 8; col++) {
				items.put(r * 9 + col, item(r, col));
			}
		}

		private ItemStack item(int r, int col) {
			if (r == 0 || r == 4) {
				return pane(col == correct ? DyeColor.MAGENTA : DyeColor.BLACK);
			}
			if (col == BUTTON) {
				return new ItemStack(Items.DYED_TERRACOTTA.pick(r == row ? DyeColor.LIME : DyeColor.RED));
			}
			if (col < 1 || col > 5) {
				return pane(DyeColor.BLACK);
			}
			if (r != row) {
				return pane(DyeColor.WHITE);
			}
			return pane(col == current ? DyeColor.LIME : DyeColor.RED);
		}
	}
}
