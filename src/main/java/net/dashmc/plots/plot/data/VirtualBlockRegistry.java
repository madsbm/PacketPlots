package net.dashmc.plots.plot.data;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.TileEntity;

/**
 * The block handlers, by the class of block each one handles, and what calls
 * the right one for a block. A block uses the handler of its class, or else of
 * its closest superclass with one, or else the default, which does what
 * vanilla does.
 */
public class VirtualBlockRegistry {
	/**
	 * The handler of blocks without one of their own.
	 */
	private static final VirtualBlock<Block> DEFAULT = new VirtualBlock<Block>() {
	};

	private final ClassRegistry<VirtualBlock<?>> handlers = new ClassRegistry<>();

	public <T extends Block> void register(Class<T> blockClass, VirtualBlock<? super T> handler) {
		handlers.register(blockClass, handler);
	}

	@SuppressWarnings("unchecked")
	private VirtualBlock<Block> handlerOf(Block block) {
		// the handler is for the block's class or a superclass of it, so it takes the
		// block
		VirtualBlock<Block> handler = (VirtualBlock<Block>) handlers.get(block.getClass());
		return handler != null ? handler : DEFAULT;
	}

	public void onRelatedUpdated(Block block, IEnvironment env, BlockPosition pos, BlockBag bag,
			IBlockData newBlockData) {
		handlerOf(block).onRelatedUpdated(block, env, pos, bag, newBlockData);
	}

	public AxisAlignedBB getBoundingBox(Block block, IEnvironment env, BlockPosition pos, IBlockData state) {
		return handlerOf(block).getCollisionBoundingBox(block, env, pos, state);
	}

	public boolean interact(IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata,
			EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		Block block = iblockdata.getBlock();
		return handlerOf(block).interact(block, environment, blockposition, iblockdata, entityhuman, enumdirection,
				f, f1, f2);
	}

	public void harvestBlock(Block block, IBlockData data, IEnvironment environment, BlockPosition pos,
			BlockBag bag, TileEntity tile) {
		handlerOf(block).onBlockHarvested(block, environment, pos, data, bag, tile);
	}

	public boolean shouldRemainAt(Block block, IEnvironment env, BlockPosition position) {
		return handlerOf(block).shouldRemainAt(env, position);
	}

	public boolean mayPlace(Block toPlace, IEnvironment environment, BlockPosition pos, EnumDirection direction,
			ItemStack itemStack) {
		return handlerOf(toPlace).canPlace(toPlace, environment, pos, direction, itemStack);
	}

	public void postPlace(Block placedBlock, IEnvironment environment, BlockPosition pos, IBlockData blockData,
			EntityHuman entityHuman, ItemStack itemStack) {
		handlerOf(placedBlock).postPlace(placedBlock, environment, pos, blockData, entityHuman, itemStack);
	}

	public void remove(IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata) {
		Block block = iblockdata.getBlock();
		handlerOf(block).remove(block, environment, blockposition, iblockdata);
	}

	public void onPlace(IEnvironment environment, BlockPosition blockposition, IBlockData iblockdata) {
		Block block = iblockdata.getBlock();
		handlerOf(block).onPlace(block, environment, blockposition, iblockdata);
	}
}
