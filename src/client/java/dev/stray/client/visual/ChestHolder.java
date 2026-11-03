package dev.stray.client.visual;

/** Implemented on AvatarRenderState: whether this player shows the chest cosmetic. */
public interface ChestHolder {
	boolean stray$chest();

	void stray$setChest(boolean chest);

	ShopChest.Chest stray$chestLook();

	void stray$setChestLook(ShopChest.Chest look);
}
