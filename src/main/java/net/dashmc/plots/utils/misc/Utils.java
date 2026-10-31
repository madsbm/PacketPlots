package net.dashmc.plots.utils.misc;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import net.dashmc.plots.nms.NmsChunks;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

public class Utils {
	// chunk utils

	/**
	 * @return a key only this chunk has, unlike ChunkCoordIntPair#hashCode, which
	 *         thousands of chunks in a world share
	 */
	public static long getChunkKey(int chunkX, int chunkZ) {
		return NmsChunks.getKey(chunkX, chunkZ);
	}

	public static long getChunkKey(BlockPosition pos) {
		return getChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
	}

	/**
	 * @return the index of the position in its chunk section's arrays, such as its
	 *         block ids
	 */
	public static int getSectionIndex(int x, int y, int z) {
		return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
	}

	/**
	 * @return the x of the index in its chunk section, from 0 to 15, the opposite
	 *         of {@link #getSectionIndex}
	 */
	public static int getIndexX(int index) {
		return index & 15;
	}

	public static int getIndexY(int index) {
		return index >> 8 & 15;
	}

	public static int getIndexZ(int index) {
		return index >> 4 & 15;
	}

	/**
	 * @param mask a bit for each section of a chunk, from the bottom, like a
	 *             chunk map's
	 */
	public static boolean hasSection(int mask, int chunkY) {
		return (mask & 1 << chunkY) != 0;
	}

	/**
	 * @param index the index of the block's first byte in a chunk map's data,
	 *              where a block id takes 2 bytes, low byte first
	 */
	public static char getMapBlockId(byte[] data, int index) {
		return (char) ((data[index] & 255) | (data[index + 1] & 255) << 8);
	}

	/**
	 * @param index the index of the block's first byte in a chunk map's data,
	 *              where a block id takes 2 bytes, low byte first
	 */
	public static void setMapBlockId(byte[] data, int index, char blockId) {
		data[index] = (byte) (blockId & 255);
		data[index + 1] = (byte) (blockId >> 8 & 255);
	}

	// coord utils
	public static Location convertPosToLoc(World world, BlockPosition pos) {
		CraftWorld craftWorld = world.getWorld();
		return convertPosToLoc(craftWorld, pos);
	}

	public static Location convertPosToLoc(CraftWorld craftWorld, BlockPosition pos) {
		return convertPosToLoc((org.bukkit.World) craftWorld, pos);
	}

	public static Location convertPosToLoc(org.bukkit.World world, BlockPosition pos) {
		return new Location(world, pos.getX(), pos.getY(), pos.getZ());
	}

	public static BlockPosition convertLocToPos(Location loc) {
		return new BlockPosition(loc.getX(), loc.getY(), loc.getZ());
	}

}
