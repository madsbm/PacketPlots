package net.dashmc.plots.plot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import net.dashmc.plots.plot.FakeEnvironment;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;

public class VirtualBlockTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	/**
	 * @return what the owner got from breaking the block, in their inventory or
	 *         their block bag
	 */
	private static List<ItemStack> harvest(Block block, int legacyData) {
		FakeEnvironment env = new FakeEnvironment();
		IBlockData data = block.fromLegacyData(legacyData);
		VirtualBlock.harvestBlock(block, data, env, new BlockPosition(0, 64, 0), env.getBlockBag(), null);

		List<ItemStack> received = new ArrayList<>();
		for (ItemStack stack : env.getOwnerInventory().items)
			if (stack != null)
				received.add(stack);
		received.addAll(env.getBlockBag().getBag());
		return received;
	}

	private static void assertHarvests(Block block, int legacyData, Item item, int count, int itemData) {
		List<ItemStack> received = harvest(block, legacyData);

		assertEquals(1, received.size());
		assertEquals(item, received.get(0).getItem());
		assertEquals(count, received.get(0).count);
		assertEquals(itemData, received.get(0).getData());
	}

	@Test
	public void variantBlocksKeepTheirVariant() {
		// red wool and stained clay used to come back white
		assertHarvests(Blocks.WOOL, 14, Item.getItemOf(Blocks.WOOL), 1, 14);
		assertHarvests(Blocks.STAINED_HARDENED_CLAY, 14, Item.getItemOf(Blocks.STAINED_HARDENED_CLAY), 1, 14);
		assertHarvests(Blocks.STONEBRICK, 1, Item.getItemOf(Blocks.STONEBRICK), 1, 1);
		assertHarvests(Blocks.SANDSTONE, 1, Item.getItemOf(Blocks.SANDSTONE), 1, 1);
	}

	@Test
	public void blocksKeepTheirVariantButNotTheirOrientation() {
		// a birch log along the x axis
		assertHarvests(Blocks.LOG, 2 | 4, Item.getItemOf(Blocks.LOG), 1, 2);
		// a top half sandstone slab
		assertHarvests(Blocks.STONE_SLAB, 1 | 8, Item.getItemOf(Blocks.STONE_SLAB), 1, 1);
	}

	// vanilla's drop data for these leaves is their sapling's
	@Test
	public void darkOakLeavesComeBackAsDarkOakLeaves() {
		assertHarvests(Blocks.LEAVES2, 1, Item.getItemOf(Blocks.LEAVES2), 1, 1);
	}

	@Test
	public void blocksWithoutAnItemGiveWhatVanillaDrops() {
		// a double sandstone slab
		assertHarvests(Blocks.DOUBLE_STONE_SLAB, 1, Item.getItemOf(Blocks.STONE_SLAB), 2, 1);
		assertHarvests(Blocks.STANDING_SIGN, 0, Items.SIGN, 1, 0);

		assertTrue(harvest(Blocks.CAKE, 0).isEmpty());
	}
}
