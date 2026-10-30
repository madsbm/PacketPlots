package net.dashmc.plots.utils.misc;

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
}
