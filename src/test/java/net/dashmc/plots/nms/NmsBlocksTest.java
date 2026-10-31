package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockStepAbstract;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.TileEntityChest;

public class NmsBlocksTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	@Test
	public void aStatesIdLeadsBackToIt() {
		IBlockData redWool = Blocks.WOOL.fromLegacyData(14);

		assertSame(redWool, NmsBlocks.getStateById(NmsBlocks.getStateId(redWool)));
	}

	@Test
	public void stoneIsAFullBlockAndASlabIsnt() {
		assertTrue(NmsBlocks.isFullBlock(Blocks.STONE));
		assertFalse(NmsBlocks.isFullBlock(Blocks.STONE_SLAB));
	}

	@Test
	public void aFullBlocksBoundingBoxIsItsSpace() {
		AxisAlignedBB box = NmsBlocks.getBoundingBox(Blocks.STONE, new BlockPosition(1, 64, -2));

		assertEquals(1, box.a, 0);
		assertEquals(64, box.b, 0);
		assertEquals(-2, box.c, 0);
		assertEquals(2, box.d, 0);
		assertEquals(65, box.e, 0);
		assertEquals(-1, box.f, 0);
	}

	@Test
	public void cycleOpensAClosedDoor() {
		IBlockData closed = Blocks.WOODEN_DOOR.getBlockData().set(BlockDoor.OPEN, false);

		assertTrue(NmsBlocks.cycle(closed, BlockDoor.OPEN).get(BlockDoor.OPEN));
	}

	@Test
	public void aChestGetsAChestTileEntity() {
		assertTrue(NmsBlocks.createTileEntity(null, Blocks.CHEST.getBlockData()) instanceof TileEntityChest);
	}

	@Test
	public void onlyTheTrappedChestIsTrapped() {
		assertTrue(NmsBlocks.isTrappedChest((BlockChest) Blocks.TRAPPED_CHEST));
		assertFalse(NmsBlocks.isTrappedChest((BlockChest) Blocks.CHEST));
	}

	@Test
	public void aSlabItemPlacesItsVariant() {
		BlockStepAbstract slab = (BlockStepAbstract) Blocks.STONE_SLAB;
		IBlockData sandstoneSlab = slab.fromLegacyData(1);

		assertEquals(sandstoneSlab.get(NmsBlocks.getSlabVariantProperty(slab)),
				NmsBlocks.getSlabVariant(slab, new ItemStack(slab, 1, 1)));
	}
}
