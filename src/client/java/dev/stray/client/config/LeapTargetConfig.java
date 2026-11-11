package dev.stray.client.config;

/**
 * One Auto Leap / Fast Leap target: on or off, and who to leap to, by class (an index into
 * the dungeon classes, -1 for none) or by player name, depending on the leap mode.
 */
public final class LeapTargetConfig {
	public boolean on = false;
	public int clazz = -1;
	public String name = "";

	public LeapTargetConfig copy() {
		LeapTargetConfig out = new LeapTargetConfig();
		out.on = on;
		out.clazz = clazz;
		out.name = name == null ? "" : name;
		return out;
	}
}
