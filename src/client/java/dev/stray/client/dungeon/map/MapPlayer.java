package dev.stray.client.dungeon.map;

import dev.stray.client.config.StrayConfig;
import dev.stray.client.dungeon.DungeonParty;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;

/** A head on the map. */
public final class MapPlayer {
	private static final long ANIMATION_MS = 350L;
	public final String name;
	public DungeonParty.DungeonClass clazz = DungeonParty.DungeonClass.EMPTY;
	public PlayerSkin skin;
	public boolean dead;
	public boolean self;
	public AbstractClientPlayer entity;
	public String icon = "";
	public float mapX;
	public float mapZ;
	public float yaw;
	private boolean placed;
	private float fromX;
	private float fromZ;
	private float fromYaw;
	private long moveStart;
	private float drawX;
	private float drawZ;
	private float drawYaw;

	public MapPlayer(String name) {
		this.name = name;
	}

	/** A new position from the map decoration. */
	public void target(float x, float z, float yaw) {
		if (!placed) {
			placed = true;
			drawX = mapX = x;
			drawZ = mapZ = z;
			drawYaw = this.yaw = yaw;
			return;
		}
		if (x == mapX && z == mapZ && yaw == this.yaw) {
			return;
		}
		fromX = drawX;
		fromZ = drawZ;
		fromYaw = drawYaw;
		moveStart = System.currentTimeMillis();
		mapX = x;
		mapZ = z;
		this.yaw = yaw;
	}

	public boolean placed() {
		return placed || (entity != null && entity.isAlive());
	}

	/** [x, z, yaw] to draw at. */
	public float[] position(float partialTick) {
		if (entity != null && entity.isAlive()) {
			double x = entity.xo + (entity.getX() - entity.xo) * partialTick;
			double z = entity.zo + (entity.getZ() - entity.zo) * partialTick;
			float[] m = MapUtils.toMap(x, z);
			drawX = m[0];
			drawZ = m[1];
			drawYaw = entity.getViewYRot(partialTick);
			return new float[]{drawX, drawZ, drawYaw};
		}
		if (!StrayConfig.get().dungeonMapAnimateHeads) {
			return new float[]{mapX, mapZ, yaw};
		}
		float t = Math.min(1f, (System.currentTimeMillis() - moveStart) / (float) ANIMATION_MS);
		drawX = fromX + (mapX - fromX) * t;
		drawZ = fromZ + (mapZ - fromZ) * t;
		float delta = ((yaw - fromYaw) % 360f + 540f) % 360f - 180f;
		drawYaw = fromYaw + delta * t;
		return new float[]{drawX, drawZ, drawYaw};
	}
}
