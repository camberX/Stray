package dev.stray.client.dungeon.map;

import dev.stray.client.dungeon.DungeonParty;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A head on the map. Teammates out of render distance move by the map item's markers,
 * which jump a few pixels at a time, so their head glides to each new spot over 350 ms.
 */
public final class MapPlayer {
	private static final float GLIDE_MS = 350f;

	public final String name;
	public DungeonParty.DungeonClass clazz;
	public PlayerSkin skin;
	public boolean dead;
	public AbstractClientPlayer entity;
	public boolean self;
	String icon = "";

	private float fromX;
	private float fromZ;
	private float fromYaw;
	private float toX;
	private float toZ;
	private float toYaw;
	private long startMs;
	private boolean placed;

	public MapPlayer(String name) {
		this.name = name;
	}

	/** Sets where the head is headed; it glides there from wherever it is now. */
	public void target(float x, float z, float yaw) {
		if (placed && x == toX && z == toZ && yaw == toYaw) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!placed) {
			fromX = x;
			fromZ = z;
			fromYaw = yaw;
			placed = true;
		} else {
			fromX = mapX(now);
			fromZ = mapZ(now);
			fromYaw = yaw(now);
		}
		toX = x;
		toZ = z;
		toYaw = yaw;
		startMs = now;
	}

	/** Puts the head straight there (the preview, or a player you can see). */
	public void place(float x, float z, float yaw) {
		fromX = toX = x;
		fromZ = toZ = z;
		fromYaw = toYaw = yaw;
		startMs = 0L;
		placed = true;
	}

	public boolean placed() {
		return placed;
	}

	private float progress(long now) {
		return Math.min(1f, (now - startMs) / GLIDE_MS);
	}

	public float mapX(long now) {
		float t = ease(progress(now));
		return fromX + (toX - fromX) * t;
	}

	public float mapZ(long now) {
		float t = ease(progress(now));
		return fromZ + (toZ - fromZ) * t;
	}

	public float yaw(long now) {
		float t = ease(progress(now));
		float delta = ((toYaw - fromYaw) % 360f + 540f) % 360f - 180f;
		return fromYaw + delta * t;
	}

	private static float ease(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}
}
