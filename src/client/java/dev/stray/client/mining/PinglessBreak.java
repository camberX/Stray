package dev.stray.client.mining;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.item.ItemText;
import dev.stray.client.location.SkyblockLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skyblock instamine still waits out the round trip before the block
 * disappears, because the client does not know the mining-speed formula.
 * When the held stats clear that block's threshold, the block is removed
 * locally at the moment vanilla sends the start packet. Progress is not
 * raised and no stop packet is added. If the server never changes the
 * block, it is put back.
 */
public final class PinglessBreak {
	private static final Pattern SPEED = Pattern.compile(
		"(?i)mining speed\\s*:?\\s*(?:[^\\p{L}\\d\\s]{1,4}\\s*)?(\\d[\\d,]*)"
	);
	private static final Pattern POWER = Pattern.compile(
		"(?i)breaking power\\s*:?\\s*(?:[^\\p{L}\\d\\s]{1,4}\\s*)?(\\d+)"
	);

	private static final int RESTORE_TICKS = 40;
	private static final ConcurrentHashMap<Long, Pending> PENDING = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap.KeySetView<Long, Boolean> CONFIRMED = ConcurrentHashMap.newKeySet();

	private static int cachedTick = Integer.MIN_VALUE;
	private static int cachedSpeed;
	private static int cachedPower;

	private PinglessBreak() {
	}

	public static boolean active() {
		if (!StrayConfig.get().pinglessBreak) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null) {
			return false;
		}
		if (client.player.getAbilities().instabuild || client.gameMode.isSpectator()) {
			return false;
		}
		return true;
	}

	/**
	 * True when this block should already be gone on a zero-ping client.
	 * Unknown blocks, the wrong island, and a tool that cannot break it
	 * stay on the slow path.
	 */
	public static boolean predict(BlockPos pos) {
		if (!active() || pos == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || !client.player.onGround()) {
			return false;
		}
		if (client.player.isEyeInFluid(FluidTags.WATER) && !aquaAffinity(client.player.getItemBySlot(EquipmentSlot.HEAD))) {
			return false;
		}
		BlockState state = client.level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		Profile profile = profile(state.getBlock());
		if (profile == null) {
			return false;
		}
		int[] stats = stats(client);
		return profile.ready(stats[0], stats[1]);
	}

	/** The block that was just removed locally. A server update keeps the hole. */
	public static void remember(BlockPos pos, BlockState state) {
		if (pos == null || state == null || state.isAir()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		int tick = client.player == null ? 0 : client.player.tickCount;
		long key = pos.asLong();
		CONFIRMED.remove(key);
		PENDING.put(key, new Pending(state, tick));
		if (PENDING.size() > 64) {
			Long extra = PENDING.keySet().iterator().next();
			if (extra != null) {
				PENDING.remove(extra);
			}
		}
	}

	public static void onPacket(Packet<?> packet) {
		if (PENDING.isEmpty() || packet == null) {
			return;
		}
		if (packet instanceof ClientboundBlockUpdatePacket update) {
			acknowledge(update.getPos());
			return;
		}
		if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
			section.runUpdates((pos, state) -> acknowledge(pos));
		}
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null) {
			PENDING.clear();
			CONFIRMED.clear();
			cachedTick = Integer.MIN_VALUE;
			return;
		}
		if (PENDING.isEmpty()) {
			return;
		}
		int now = client.player.tickCount;
		PENDING.entrySet().removeIf(entry -> {
			long key = entry.getKey();
			if (CONFIRMED.remove(key)) {
				return true;
			}
			Pending pending = entry.getValue();
			int age = now - pending.tick;
			BlockPos pos = BlockPos.of(key);
			if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				return age < 0 || age >= RESTORE_TICKS;
			}
			BlockState current = client.level.getBlockState(pos);
			if (!current.isAir()) {
				return true;
			}
			if (age >= 0 && age < RESTORE_TICKS) {
				return false;
			}
			client.level.setBlock(pos, pending.state, 3);
			return true;
		});
	}

	private static void acknowledge(BlockPos pos) {
		if (pos == null) {
			return;
		}
		long key = pos.asLong();
		if (PENDING.containsKey(key)) {
			CONFIRMED.add(key);
		}
	}

	/**
	 * Wiki thresholds: a normal block instamines above 30x its strength, and
	 * obsidian, ores, gemstones, and dwarven metals above 60x. The same
	 * vanilla block is a harder ore on some islands, so those use the
	 * higher number. Light blue wool mithril cannot instamine.
	 */
	private static Profile profile(Block block) {
		if (block == null || !SkyblockLocation.inSkyblock) {
			return null;
		}
		if (block == Blocks.STONE) {
			if (hardStoneIsland()) {
				return new Profile(50, 4, false);
			}
			if (goldOrCaverns()) {
				return new Profile(15, 1, false);
			}
			return null;
		}
		if (block == Blocks.COBBLESTONE) {
			if (glaciteArea()) {
				return new Profile(5600, 9, true);
			}
			if (goldOrCaverns()) {
				return new Profile(20, 1, false);
			}
			return null;
		}
		if (block == Blocks.NETHERRACK && crimson()) {
			return new Profile(8, 1, false);
		}
		if (block == Blocks.OBSIDIAN && miningIsland()) {
			return new Profile(500, 4, true);
		}
		if (block == Blocks.POLISHED_DIORITE && (dwarven() || glaciteArea())) {
			return new Profile(2000, 5, true);
		}
		if (mithrilIsland()) {
			if (block == Blocks.GRAY_WOOL || block == Blocks.CYAN_TERRACOTTA) {
				return new Profile(500, 4, true);
			}
			if (block == Blocks.PRISMARINE || block == Blocks.PRISMARINE_BRICKS || block == Blocks.DARK_PRISMARINE) {
				return new Profile(800, 4, true);
			}
		}
		if (glaciteArea()) {
			if (block == Blocks.SMOOTH_RED_SANDSTONE || block == Blocks.TERRACOTTA || block == Blocks.BROWN_TERRACOTTA) {
				return new Profile(5600, 9, true);
			}
			if (block == Blocks.CLAY) {
				return new Profile(5600, 9, true);
			}
		}
		Profile gem = gemstone(block);
		if (gem != null && gemstoneIsland()) {
			return gem;
		}
		Profile ore = ore(block, mithrilIsland());
		if (ore != null && (mithrilIsland() || goldOrCaverns())) {
			return ore;
		}
		if (block == Blocks.NETHER_QUARTZ_ORE && (goldOrCaverns() || crimson())) {
			return new Profile(30, 1, true);
		}
		return null;
	}

	private static Profile gemstone(Block block) {
		if (block == Blocks.RED_STAINED_GLASS) {
			return new Profile(2300, 6, true);
		}
		if (block == Blocks.ORANGE_STAINED_GLASS
			|| block == Blocks.PURPLE_STAINED_GLASS
			|| block == Blocks.LIME_STAINED_GLASS
			|| block == Blocks.WHITE_STAINED_GLASS
			|| block == Blocks.LIGHT_BLUE_STAINED_GLASS) {
			return new Profile(3000, 7, true);
		}
		if (block == Blocks.YELLOW_STAINED_GLASS) {
			return new Profile(3800, 8, true);
		}
		if (block == Blocks.MAGENTA_STAINED_GLASS) {
			return new Profile(4800, 9, true);
		}
		if (block == Blocks.BLUE_STAINED_GLASS
			|| block == Blocks.GREEN_STAINED_GLASS
			|| block == Blocks.BROWN_STAINED_GLASS
			|| block == Blocks.BLACK_STAINED_GLASS) {
			return new Profile(5200, 9, true);
		}
		return null;
	}

	private static Profile ore(Block block, boolean pure) {
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return pure ? new Profile(600, 3, true) : new Profile(30, 1, true);
		}
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
			|| block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
			return pure ? new Profile(600, 3, true) : new Profile(30, 2, true);
		}
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
			|| block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
			return pure ? new Profile(600, 3, true) : new Profile(30, 3, true);
		}
		return null;
	}

	private static boolean miningIsland() {
		return hardStoneIsland() || dwarven() || glaciteArea() || goldOrCaverns() || crimson();
	}

	private static boolean hardStoneIsland() {
		return SkyblockLocation.inCrystalHollows() || glaciteArea();
	}

	private static boolean gemstoneIsland() {
		return SkyblockLocation.inCrystalHollows() || SkyblockLocation.inMinesOfDivan();
	}

	private static boolean mithrilIsland() {
		return SkyblockLocation.inCrystalHollows() || dwarven() || glaciteArea();
	}

	private static boolean dwarven() {
		return place().contains("dwarven");
	}

	private static boolean glaciteArea() {
		String place = place();
		return place.contains("glacite") || place.contains("mineshaft");
	}

	private static boolean goldOrCaverns() {
		String place = place();
		return place.contains("gold mine") || place.contains("deep cavern");
	}

	private static boolean crimson() {
		return place().contains("crimson");
	}

	private static String place() {
		String area = SkyblockLocation.area == null ? "" : SkyblockLocation.area;
		String poi = SkyblockLocation.poi == null ? "" : SkyblockLocation.poi;
		return (area + " " + poi).toLowerCase(Locale.ROOT);
	}

	private static int[] stats(Minecraft client) {
		int tick = client.player == null ? -1 : client.player.tickCount;
		if (tick == cachedTick) {
			return new int[]{cachedSpeed, cachedPower};
		}
		int speed = 0;
		int power = 0;
		if (client.player != null) {
			int[] held = fromText(client.player.getMainHandItem());
			speed = held[0];
			power = held[1];
			ClientPacketListener connection = client.player.connection;
			if (connection != null) {
				for (PlayerInfo info : connection.getListedOnlinePlayers()) {
					String line = plain(tabName(info));
					speed = Math.max(speed, first(SPEED, line));
					power = Math.max(power, first(POWER, line));
				}
			}
		}
		cachedTick = tick;
		cachedSpeed = speed;
		cachedPower = power;
		return new int[]{speed, power};
	}

	private static int[] fromText(ItemStack stack) {
		int speed = 0;
		int power = 0;
		ItemText text = ItemText.capture(stack);
		if (text.lore() == null) {
			return new int[]{0, 0};
		}
		for (Component line : text.lore().lines()) {
			String plain = plain(line);
			speed = Math.max(speed, first(SPEED, plain));
			power = Math.max(power, first(POWER, plain));
		}
		return new int[]{speed, power};
	}

	private static boolean aquaAffinity(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
		if (enchants != null && !enchants.isEmpty()) {
			for (Holder<Enchantment> holder : enchants.keySet()) {
				Identifier id = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
				if (id != null && "aqua_affinity".equals(id.getPath()) && enchants.getLevel(holder) > 0) {
					return true;
				}
			}
		}
		ItemText text = ItemText.capture(stack);
		if (text.lore() == null) {
			return false;
		}
		for (Component line : text.lore().lines()) {
			if (plain(line).toLowerCase(Locale.ROOT).contains("aqua affinity")) {
				return true;
			}
		}
		return false;
	}

	private static Component tabName(PlayerInfo info) {
		Component display = info.getTabListDisplayName();
		if (display != null) {
			return display;
		}
		return PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
	}

	private static int first(Pattern pattern, String line) {
		if (line == null || line.isEmpty()) {
			return 0;
		}
		Matcher matcher = pattern.matcher(line);
		if (!matcher.find()) {
			return 0;
		}
		String digits = matcher.group(1).replace(",", "");
		if (digits.isEmpty() || digits.length() > 9) {
			return 0;
		}
		try {
			int value = Integer.parseInt(digits);
			return value > 0 ? value : 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static String plain(Component component) {
		if (component == null) {
			return "";
		}
		return component.getString().replaceAll("§.", "").replace('\u00A0', ' ').trim();
	}

	private record Pending(BlockState state, int tick) {
	}

	private record Profile(int strength, int power, boolean ore) {
		boolean ready(int speed, int heldPower) {
			if (strength <= 0 || heldPower < power || speed <= 0) {
				return false;
			}
			long need = (long) strength * (ore ? 60L : 30L);
			return speed > need;
		}
	}
}
