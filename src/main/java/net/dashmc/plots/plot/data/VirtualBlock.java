package net.dashmc.plots.plot.data;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.blocks.VirtualBlockAir;
import net.dashmc.plots.plot.data.blocks.VirtualBlockDoor;
import net.dashmc.plots.plot.data.blocks.VirtualBlockLeaves2;
import net.dashmc.plots.plot.data.blocks.VirtualBlockTrapDoor;
import net.dashmc.plots.plot.data.blocks.VirtualChestBlock;
import net.dashmc.plots.plot.data.blocks.VirtualEnderChestBlock;
import net.dashmc.plots.plot.data.blocks.VirtualFenceGateBlock;
import net.dashmc.plots.plot.data.blocks.VirtualSkullBlock;
import net.dashmc.plots.utils.helpers.ClassRegistry;
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
 * does, so a block only needs a handler where it differs.
 */
public abstract class VirtualBlock<T extends Block> {
	private static final ClassRegistry<VirtualBlock<?>> virtualBlocks = new ClassRegistry<>();

	/**
	 * The handler of blocks without one of their own.
	 */
	private static final VirtualBlock<Block> DEFAULT = new VirtualBlock<Block>() {
		@Override
		public Class<Block> getClazz() {
			return Block.class;
		}
	};

	public boolean interact(T block, IEnvironment environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		return false;
	}

	public void onPlace(T block, IEnvironment environment, BlockPosition pos, IBlockData blockData) {
	}

	public void remove(T block, IEnvironment environment, BlockPosition pos, IBlockData blockData) {
	}

	public boolean canPlace(T block, IEnvironment environment, BlockPosition pos, EnumDirection direction,
			ItemStack itemStack) {
		return canPlace(block, environment, pos, direction);
	}

	public boolean canPlace(T block, IEnvironment environment, BlockPosition pos, EnumDirection direction) {
		return canPlace(block, environment, pos);
	}

	public boolean canPlace(T block, IEnvironment environment, BlockPosition pos) {
		return environment.getType(pos).getBlock().getMaterial().isReplaceable();
	}

	public void onRelatedUpdated(T block, IEnvironment env, BlockPosition pos, BlockBag bag,
			IBlockData newBlockData) {
	}

	/**
	 * Puts the block in the bag as its own item. A block without one, such as a
	 * double slab or a sign, gives what vanilla drops instead.
	 */
	public void onBlockHarvested(T block, IEnvironment environment, BlockPosition pos, IBlockData data,
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

	public int getDropData(IEnvironment env, BlockPosition pos) {
		return getDropData(env.getType(pos));
	}

	/**
	 * @return the data of the block's item, which is the variant or color for
	 *         blocks that have one
	 */
	public int getDropData(IBlockData data) {
		return data.getBlock().getDropData(data);
	}

	public AxisAlignedBB getCollisionBoundingBox(T block, IEnvironment env, BlockPosition pos, IBlockData state) {
		double minX = block.B();
		double minY = block.D();
		double minZ = block.F();

		double maxX = block.C();
		double maxY = block.E();
		double maxZ = block.G();

		return new AxisAlignedBB((double) pos.getX() + minX, (double) pos.getY() + minY, (double) pos.getZ() + minZ,
				(double) pos.getX() + maxX, (double) pos.getY() + maxY, (double) pos.getZ() + maxZ);
	}

	public boolean shouldRemainAt(IEnvironment env, BlockPosition pos) {
		return false;
	}

	public void postPlace(T block, IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata,
			EntityLiving entityliving, ItemStack itemstack) {
	}

	public void postBreak(T block, IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata) {
	}

	public abstract Class<T> getClazz();

	public void register() {
		virtualBlocks.register(getClazz(), this);
	}

	/**
	 * @return the handler of the block's class, or else of its closest superclass
	 *         with one, or else the default
	 */
	@SuppressWarnings("unchecked")
	private static VirtualBlock<Block> handlerOf(Block block) {
		// the handler is for the block's class or a superclass of it, so it takes the
		// block
		VirtualBlock<Block> handler = (VirtualBlock<Block>) virtualBlocks.get(block.getClass());
		return handler != null ? handler : DEFAULT;
	}

	public static final void notify(Block block, IEnvironment env, BlockPosition pos, BlockBag bag,
			IBlockData newBlockData) {
		handlerOf(block).onRelatedUpdated(block, env, pos, bag, newBlockData);
	}

	public static final AxisAlignedBB getBoundingBox(Block block, IEnvironment env, BlockPosition pos,
			IBlockData state) {
		return handlerOf(block).getCollisionBoundingBox(block, env, pos, state);
	}

	public static final boolean interact(IEnvironment environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		Block block = iblockdata.getBlock();
		return handlerOf(block).interact(block, environment, blockposition, iblockdata, entityhuman, enumdirection,
				f, f1, f2);
	}

	public static final void harvestBlock(Block block, IBlockData data, IEnvironment environment,
			BlockPosition pos, BlockBag bag, TileEntity tile) {
		handlerOf(block).onBlockHarvested(block, environment, pos, data, bag, tile);
	}

	public static final boolean shouldRemainAt(Block block, IEnvironment env, BlockPosition position) {
		return handlerOf(block).shouldRemainAt(env, position);
	}

	public static final boolean mayPlace(Block toPlace, IEnvironment environment, BlockPosition pos,
			EnumDirection direction, ItemStack itemStack) {
		return handlerOf(toPlace).canPlace(toPlace, environment, pos, direction, itemStack);
	}

	public static final void postPlace(Block placedBlock, IEnvironment environment, BlockPosition pos,
			IBlockData blockData, EntityHuman entityHuman, ItemStack itemStack) {
		handlerOf(placedBlock).postPlace(placedBlock, environment, pos, blockData, (EntityLiving) entityHuman,
				itemStack);
	}

	public static final void remove(IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata) {
		Block block = iblockdata.getBlock();
		handlerOf(block).remove(block, environment, blockposition, iblockdata);
	}

	public static final void onPlace(IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata) {
		Block block = iblockdata.getBlock();
		handlerOf(block).onPlace(block, environment, blockposition, iblockdata);
	}

	static {
		new VirtualBlockAir().register();
		new VirtualChestBlock().register();
		new VirtualBlockDoor().register();
		new VirtualEnderChestBlock().register();
		new VirtualFenceGateBlock().register();
		new VirtualSkullBlock().register();
		new VirtualBlockLeaves2().register();
		new VirtualBlockTrapDoor().register();
	}

}
