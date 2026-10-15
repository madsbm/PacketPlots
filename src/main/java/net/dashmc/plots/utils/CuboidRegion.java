package net.dashmc.plots.utils;

import org.apache.logging.log4j.util.TriConsumer;
import org.bukkit.Location;
import lombok.Getter;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.plot.data.VirtualChunk.Section;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;

public class CuboidRegion {

	@Getter
	private final BlockPosition pos1, pos2;

	@Getter
	private final int maxX, maxY, maxZ;
	@Getter
	private final int minX, minY, minZ;

	private BlockPosition min, max;

	public CuboidRegion(Location pos1, Location pos2) {
		this(Utils.convertLocToPos(pos1), Utils.convertLocToPos(pos2));
	}

	public CuboidRegion(BlockPosition pos1, BlockPosition pos2) {
		this.pos1 = pos1;
		this.pos2 = pos2;

		max = getMax();
		maxX = max.getX();
		maxY = max.getY();
		maxZ = max.getZ();

		min = getMin();
		minX = min.getX();
		minY = min.getY();
		minZ = min.getZ();
	}

	/**
	 * Returns a bitmask of the chunk sections used by this cuboid region
	 * 
	 * @return the bitmask
	 */
	public char getSectionMask() {
		byte range = getChunkHeight();
		char mask = (char) (((1 << range) - 1) << (minY >> 4));
		return mask;
	}

	public ChunkCoordIntPair[] getChunks() {
		int chunkWidth = getChunkWidth(),
				chunkDepth = getChunkDepth();
		int totalChunks = chunkWidth * chunkDepth;

		ChunkCoordIntPair[] chunks = new ChunkCoordIntPair[totalChunks];

		for (int x = minX >> 4, i = 0; x <= maxX >> 4; x++) {
			for (int z = minZ >> 4; z <= maxZ >> 4; z++, i++) {
				chunks[i] = new ChunkCoordIntPair(x, z);
			}
		}

		return chunks;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof CuboidRegion))
			return false;
		CuboidRegion otherRegion = (CuboidRegion) other;
		return (getMin().equals(otherRegion.getMin())) && (getMax().equals(otherRegion.getMax()));
	}

	/**
	 * Iterate over each location inside of both the cuboid region and the given
	 * chunk
	 * 
	 * @param consumer Consumes x,y,z coordinate of location
	 */
	public void forEachInside(Section section, TriConsumer<Integer, Integer, Integer> consumer) {
		VirtualChunk chunk = section.getVirtualChunk();

		int chunkMinX = chunk.getCoordPair().x << 4;
		int chunkMaxX = chunkMinX + 15;

		int sectionMinY = section.getChunkY() << 4;
		int sectionMaxY = sectionMinY + 15;

		int chunkMinZ = chunk.getCoordPair().z << 4;
		int chunkMaxZ = chunkMinZ + 15;

		int minX = Math.max(getMinX(), chunkMinX);
		int maxX = Math.max(getMaxX(), chunkMaxX);

		int minY = Math.max(getMinY(), sectionMinY);
		int maxY = Math.max(getMaxY(), sectionMaxY);

		int minZ = Math.max(getMinZ(), chunkMinZ);
		int maxZ = Math.max(getMaxZ(), chunkMaxZ);

		forEach(minX, minY, minZ, maxX, maxY, maxZ, consumer);
	}

	/**
	 * Iterate over each location inside of the cuboid region
	 * 
	 * @param consumer Consumes x,y,z coordinate of location
	 */
	public void forEach(TriConsumer<Integer, Integer, Integer> consumer) {
		forEach(minX, minY, minZ, maxX, maxY, maxZ, consumer);
	}

	private void forEach(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
			TriConsumer<Integer, Integer, Integer> consumer) {
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					consumer.accept(x, y, z);
				}
			}
		}
	}

	public boolean includes(int x, int y, int z, int buffer) {
		return x >= (minX - buffer) && x <= (maxX + buffer) &&
				y >= (minY - buffer) && y <= (maxY + buffer) &&
				z >= (minZ - buffer) && z <= (maxZ + buffer);
	}

	public boolean includes(int x, int y, int z) {
		return includes(x, y, z, 0);
	}

	public boolean includes(BlockPosition pos) {
		return includes(pos.getX(), pos.getY(), pos.getZ());
	}

	public boolean includes(Location pos) {
		return includes(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
	}

	public boolean includesWithBuffer(Location pos) {
		int buffer = 1;
		return includes(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ(), buffer);
	}

	public BlockPosition getMax() {
		if (max != null)
			return max;

		int x = Math.max(pos1.getX(), pos2.getX());
		int y = Math.max(pos1.getY(), pos2.getY());
		int z = Math.max(pos1.getZ(), pos2.getZ());
		return new BlockPosition(x, y, z);
	}

	public BlockPosition getMin() {
		if (min != null)
			return min;

		int x = Math.min(pos1.getX(), pos2.getX());
		int y = Math.min(pos1.getY(), pos2.getY());
		int z = Math.min(pos1.getZ(), pos2.getZ());
		return new BlockPosition(x, y, z);
	}

	public int getChunkWidth() {
		return (maxX >> 4) - (minX >> 4) + 1;
	}

	public int getChunkDepth() {
		return (maxZ >> 4) - (minZ >> 4) + 1;
	}

	public byte getChunkHeight() {
		return (byte) ((maxY >> 4) - (minY >> 4) + 1);
	}

	public int getWidth() {
		return maxX - minX + 1;
	}

	public int getDepth() {
		return maxZ - minZ + 1;
	}

	public int getHeight() {
		return maxY - minY + 1;
	}

	public String toString() {
		return "{min:{" + min + "},max:{" + max + "}";
	}
}