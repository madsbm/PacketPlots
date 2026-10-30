package net.dashmc.plots.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;

public class CuboidRegionTest {

	private static CuboidRegion region(int x1, int y1, int z1, int x2, int y2, int z2) {
		return new CuboidRegion(new BlockPosition(x1, y1, z1), new BlockPosition(x2, y2, z2));
	}

	@Test
	public void regionsWithSwappedCornersAreEqualWithTheSameHashCode() {
		CuboidRegion region = region(0, 0, 0, 10, 20, 30);
		CuboidRegion swapped = region(10, 20, 30, 0, 0, 0);

		assertEquals(region, swapped);
		assertEquals(region.hashCode(), swapped.hashCode());

		Set<CuboidRegion> regions = new HashSet<>();
		regions.add(region);
		assertTrue(regions.contains(swapped));
	}

	@Test
	public void forEachVisitsEveryBlockOnce() {
		CuboidRegion region = region(-1, 5, 3, 0, 7, 6);
		Set<BlockPosition> visited = new HashSet<>();

		region.forEach((x, y, z) -> assertTrue(visited.add(new BlockPosition(x, y, z))));

		assertEquals(2 * 3 * 4, visited.size());
		for (BlockPosition pos : visited)
			assertTrue(region.includes(pos));
	}

	@Test
	public void includesItsCornersButNothingOutside() {
		CuboidRegion region = region(0, 0, 0, 10, 20, 30);

		assertTrue(region.includes(0, 0, 0));
		assertTrue(region.includes(10, 20, 30));
		assertFalse(region.includes(11, 20, 30));
		assertFalse(region.includes(0, -1, 0));
		assertTrue(region.includes(11, 21, 31, 1));
	}

	@Test
	public void getChunksCoversEveryChunkTouched() {
		CuboidRegion region = region(0, 0, 0, 31, 10, 15);

		assertArrayEquals(new ChunkCoordIntPair[] { new ChunkCoordIntPair(0, 0), new ChunkCoordIntPair(1, 0) },
				region.getChunks());
	}

	@Test
	public void getSectionMaskCoversEverySectionTouched() {
		// y 10 to 40 touches sections 0, 1 and 2
		assertEquals(0b111, region(0, 10, 0, 1, 40, 1).getSectionMask());
		// y 16 to 31 is exactly section 1
		assertEquals(0b10, region(0, 16, 0, 1, 31, 1).getSectionMask());
	}
}
