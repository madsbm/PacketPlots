package net.dashmc.plots.utils.misc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;

public class UtilsTest {

	// keying the virtual chunks by hash code sent these chunks the plot's blocks
	@Test
	public void chunksWithTheSameHashCodeGetDifferentKeys() {
		assertEquals(new ChunkCoordIntPair(1, 1).hashCode(), new ChunkCoordIntPair(-319, 833).hashCode());
		assertNotEquals(Utils.getChunkKey(1, 1), Utils.getChunkKey(-319, 833));

		assertEquals(new ChunkCoordIntPair(0, 0).hashCode(), new ChunkCoordIntPair(1736, 1736).hashCode());
		assertNotEquals(Utils.getChunkKey(0, 0), Utils.getChunkKey(1736, 1736));
	}

	@Test
	public void aBlockHasTheKeyOfItsChunk() {
		assertEquals(Utils.getChunkKey(-1, 2), Utils.getChunkKey(new BlockPosition(-1, 70, 47)));
	}

	// like ChunkSection#getType: y, then z, then x
	@Test
	public void sectionIndicesGoByYThenZThenX() {
		assertEquals(0, Utils.getSectionIndex(0, 0, 0));
		assertEquals(1, Utils.getSectionIndex(1, 0, 0));
		assertEquals(16, Utils.getSectionIndex(0, 0, 1));
		assertEquals(256, Utils.getSectionIndex(0, 1, 0));
		assertEquals(4095, Utils.getSectionIndex(15, 15, 15));
	}

	@Test
	public void aPositionHasTheSectionIndexOfItsPlaceInItsSection() {
		assertEquals(Utils.getSectionIndex(1, 2, 3), Utils.getSectionIndex(-15, 18, 35));
	}

	@Test
	public void mapBlockIdsTakeTwoBytesLowByteFirst() {
		byte[] data = new byte[4];
		// a block id is the block's id shifted left by 4, with its data in the low bits
		char redWool = (char) (35 << 4 | 14);

		Utils.setMapBlockId(data, 2, redWool);

		assertArrayEquals(new byte[] { 0, 0, (byte) (redWool & 255), (byte) (redWool >> 8) }, data);
		assertEquals(redWool, Utils.getMapBlockId(data, 2));
	}

	@Test
	public void mapBlockIdsKeepTheirHighestBit() {
		byte[] data = new byte[2];

		Utils.setMapBlockId(data, 0, (char) 0xFFFF);

		assertEquals((char) 0xFFFF, Utils.getMapBlockId(data, 0));
	}
}
