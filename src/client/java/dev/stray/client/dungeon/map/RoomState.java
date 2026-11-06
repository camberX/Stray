package dev.stray.client.dungeon.map;

/** Ordinal order matters: it is the order things can happen to a room. */
public enum RoomState {
	GREEN,
	CLEARED,
	DISCOVERED,
	FAILED,
	UNOPENED,
	UNDISCOVERED
}
