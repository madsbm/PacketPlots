package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;

public class NmsItemsTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	@Test
	public void foodTakes32TicksToEatAndStoneCantBeUsed() {
		assertEquals(32, NmsItems.getUseDuration(new ItemStack(Items.APPLE)));
		assertEquals(0, NmsItems.getUseDuration(new ItemStack(Blocks.STONE)));
	}

	@Test
	public void copyCopiesAndKeepsNull() {
		ItemStack diamonds = new ItemStack(Items.DIAMOND, 5);

		ItemStack copy = NmsItems.copy(diamonds);

		assertNotSame(diamonds, copy);
		assertTrue(ItemStack.matches(diamonds, copy));
		assertNull(NmsItems.copy(null));
	}

	// adventure mode's CanDestroy and CanPlaceOn tags
	@Test
	public void anItemWithoutTagsMayNeitherBreakNorBePlacedOnBlocks() {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);

		assertFalse(NmsItems.canDestroy(pickaxe, Blocks.STONE));
		assertFalse(NmsItems.canPlaceOn(pickaxe, Blocks.STONE));
		assertFalse(NmsItems.canEditBlocks(pickaxe));
	}
}
