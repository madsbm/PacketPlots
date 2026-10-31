package net.dashmc.plots.plot.data;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import net.dashmc.plots.plot.IVirtualWorld;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.TileEntity;

/**
 * How a block behaves in a virtual environment. The defaults do what vanilla
 * does, so a block only needs a handler where it differs. Registered for its
 * class of block in a VirtualBlockRegistry.
 */
public abstract class VirtualBlock<T extends Block> {

	public boolean interact(T block, IVirtualWorld environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		return false;
	}

	public void onPlace(T block, IVirtualWorld environment, BlockPosition pos, IBlockData blockData) {
	}

	public void remove(T block, IVirtualWorld environment, BlockPosition pos, IBlockData blockData) {
	}

	public boolean canPlace(T block, IVirtualWorld environment, BlockPosition pos, EnumDirection direction,
			ItemStack itemStack) {
		return canPlace(block, environment, pos, direction);
	}

	public boolean canPlace(T block, IVirtualWorld environment, BlockPosition pos, EnumDirection direction) {
		return canPlace(block, environment, pos);
	}

	public boolean canPlace(T block, IVirtualWorld environment, BlockPosition pos) {
		return environment.getType(pos).getBlock().getMaterial().isReplaceable();
	}

	public void onRelatedUpdated(T block, IVirtualWorld env, BlockPosition pos, BlockBag bag,
			IBlockData newBlockData) {
	}

	/**
	 * Puts the block in the bag as its own item. A block without one, such as a
	 * double slab or a sign, gives what vanilla drops instead.
	 */
	public void onBlockHarvested(T block, IVirtualWorld environment, BlockPosition pos, IBlockData data,
			BlockBag bag,
			TileEntity tile) {
		Item item = getItemType(block, data);
		if (item != null) {
			bag.add(new ItemStack(item, 1, getDropData(data)));
			return;
		}

		Random random = ThreadLocalRandom.current();
		Item drop = block.getDropType(data, random, 0);
		if (drop != null)
			bag.add(new ItemStack(drop, block.getDropCount(0, random), block.getDropData(data)));
	};

	public Item getItemType(T block, IBlockData ibd) {
		return Item.getItemOf(block);
	}

	public int getDropData(IVirtualWorld env, BlockPosition pos) {
		return getDropData(env.getType(pos));
	}

	/**
	 * @return the data of the block's item, which is the variant or color for
	 *         blocks that have one
	 */
	public int getDropData(IBlockData data) {
		return data.getBlock().getDropData(data);
	}

	public AxisAlignedBB getCollisionBoundingBox(T block, IVirtualWorld env, BlockPosition pos, IBlockData state) {
		double minX = block.B();
		double minY = block.D();
		double minZ = block.F();

		double maxX = block.C();
		double maxY = block.E();
		double maxZ = block.G();

		return new AxisAlignedBB((double) pos.getX() + minX, (double) pos.getY() + minY, (double) pos.getZ() + minZ,
				(double) pos.getX() + maxX, (double) pos.getY() + maxY, (double) pos.getZ() + maxZ);
	}

	public boolean shouldRemainAt(IVirtualWorld env, BlockPosition pos) {
		return false;
	}

	public void postPlace(T block, IVirtualWorld environment, BlockPosition blockposition, IBlockData iblockdata,
			EntityLiving entityliving, ItemStack itemstack) {
	}

	public void postBreak(T block, IVirtualWorld environment, BlockPosition blockposition, IBlockData iblockdata) {
	}

}
