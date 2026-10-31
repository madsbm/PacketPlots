package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntityChest;
import net.minecraft.server.v1_8_R3.TileEntityEnderChest;

/**
 * Tile entities.
 */
public class NmsTileEntities {

	/**
	 * @return whether the tile entity was removed, so it's no longer in use
	 */
	public static boolean isInvalid(TileEntity tile) {
		return tile.x();
	}

	/**
	 * Marks the tile entity removed.
	 */
	public static void invalidate(TileEntity tile) {
		tile.y();
	}

	/**
	 * Marks the tile entity in use, e.g. when it's put in a chunk.
	 */
	public static void validate(TileEntity tile) {
		tile.D();
	}

	/**
	 * Forgets the block the tile entity has looked up, for when the block changes.
	 */
	public static void resetBlockCache(TileEntity tile) {
		tile.E();
	}

	public static void setPosition(TileEntity tile, BlockPosition pos) {
		tile.a(pos);
	}

	public static void save(TileEntity tile, NBTTagCompound nbt) {
		tile.b(nbt);
	}

	public static void load(TileEntity tile, NBTTagCompound nbt) {
		tile.a(nbt);
	}

	/**
	 * @return the tile entity the NBT saved, or null if its kind isn't known
	 */
	public static TileEntity createFromNbt(NBTTagCompound nbt) {
		return TileEntity.c(nbt);
	}

	public static void setCustomName(TileEntityChest chest, String name) {
		chest.a(name);
	}

	/**
	 * @param count how many more players have it open, or fewer if negative
	 */
	public static void addPlayersUsing(TileEntityChest chest, int count) {
		chest.l += count;
	}

	/**
	 * @param count how many more players have it open, or fewer if negative
	 */
	public static void addPlayersUsing(TileEntityEnderChest enderChest, int count) {
		enderChest.g += count;
	}
}
