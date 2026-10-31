package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.World;

/**
 * Item stacks.
 */
public class NmsItems {

	/**
	 * @return how many ticks the item can be used for, e.g. eating food, or 0 if
	 *         it can't
	 */
	public static int getUseDuration(ItemStack item) {
		return item.l();
	}

	/**
	 * @return a copy of the item, or null if it's null
	 */
	public static ItemStack copy(ItemStack item) {
		return ItemStack.b(item);
	}

	/**
	 * @return whether the item may break the block in adventure mode
	 */
	public static boolean canDestroy(ItemStack item, Block block) {
		return item.c(block);
	}

	/**
	 * @return whether the item may be placed on the block in adventure mode
	 */
	public static boolean canPlaceOn(ItemStack item, Block block) {
		return item.d(block);
	}

	/**
	 * @return whether the item may change blocks without the player being allowed
	 *         to build
	 */
	public static boolean canEditBlocks(ItemStack item) {
		return item.x();
	}

	/**
	 * Lets the item know it broke the block, e.g. so a tool takes damage.
	 */
	public static void onBlockBroken(ItemStack item, World world, Block block, BlockPosition pos,
			EntityHuman player) {
		item.a(world, block, pos, player);
	}
}
