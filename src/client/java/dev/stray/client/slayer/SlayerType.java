package dev.stray.client.slayer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Set;

/** The six slayer bosses, with the names Hypixel uses for them and their mini-bosses. */
public enum SlayerType {
	REVENANT("Revenant Horror", "REVENANT", "zombie", List.of("Atoned Horror"),
		Set.of("Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant")),
	TARANTULA("Tarantula Broodfather", "TARANTULA", "spider", List.of("Conjoined Brood"),
		Set.of("Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula", "Primordial Jockey", "Primordial Viscount")),
	SVEN("Sven Packmaster", "SVEN", "wolf", List.of(),
		Set.of("Pack Enforcer", "Sven Follower", "Sven Alpha")),
	VOID("Voidgloom Seraph", "VOID", "enderman", List.of(),
		Set.of("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")),
	INFERNO("Inferno Demonlord", "INFERNO", "blaze", List.of(),
		Set.of("Flare Demon", "Kindleheart Demon", "Burningsoul Demon")),
	VAMPIRE("Bloodfiend", "VAMPIRE", "vampire", List.of("Riftstalker Bloodfiend"), Set.of());

	/** Name shown in the scoreboard quest line and on the boss. */
	public final String displayName;
	/** Key used by the slayer data (Slayer.json). */
	public final String dataKey;
	/** Name used by /rng. */
	public final String rngName;
	public final List<String> otherNames;
	public final Set<String> miniBosses;

	SlayerType(String displayName, String dataKey, String rngName, List<String> otherNames, Set<String> miniBosses) {
		this.displayName = displayName;
		this.dataKey = dataKey;
		this.rngName = rngName;
		this.otherNames = otherNames;
		this.miniBosses = miniBosses;
	}

	/** The type named in a line like "Voidgloom Seraph IV" (or null). */
	public static SlayerType byName(String line) {
		if (line == null || line.isEmpty()) {
			return null;
		}
		for (SlayerType type : values()) {
			if (line.contains(type.displayName)) {
				return type;
			}
			for (String other : type.otherNames) {
				if (line.contains(other)) {
					return type;
				}
			}
		}
		return null;
	}

	/** Whether an entity is of the kind this boss (and its grind mobs) are. */
	public boolean matches(Entity entity) {
		return switch (this) {
			case REVENANT -> entity instanceof Zombie;
			case TARANTULA -> entity instanceof Spider;
			case SVEN -> entity instanceof Wolf;
			case VOID -> entity instanceof EnderMan;
			case INFERNO -> entity instanceof Blaze;
			case VAMPIRE -> entity instanceof Player;
		};
	}

	/** Any slayer mini-boss name (the name without level and health). */
	public static boolean isMiniBoss(String name) {
		for (SlayerType type : values()) {
			if (type.miniBosses.contains(name)) {
				return true;
			}
		}
		return false;
	}

	/** The slayer type that is fought in a sub-area of an island (scoreboard location line). */
	public static SlayerType forArea(String island, String poi) {
		String area = poi == null ? "" : poi;
		String isle = island == null ? "" : island;
		switch (area) {
			case "Graveyard":
				return isle.equalsIgnoreCase("Hub") ? REVENANT : null;
			case "Revenant Cave", "Crypts":
				return REVENANT;
			case "Spider Mound", "Arachne's Burrow", "Arachne's Sanctuary", "Burning Desert":
				return TARANTULA;
			case "Ruins", "Howling Cave", "Soul Cave", "Spirit Cave":
				return SVEN;
			case "Void Sepulture", "Zealot Bruiser Hideout", "Dragon's Nest":
				return VOID;
			case "Stronghold", "The Wasteland", "Smoldering Tomb":
				return INFERNO;
			case "Stillgore Château", "Stillgore Chateau", "Oubliette":
				return VAMPIRE;
			default:
				return isle.equalsIgnoreCase("The End") && area.isEmpty() ? VOID : null;
		}
	}
}
