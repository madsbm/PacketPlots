package net.dashmc.plots.nms;

import net.dashmc.plots.utils.helpers.MethodWrapper;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockStepAbstract;
import net.minecraft.server.v1_8_R3.BlockTrapdoor;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IBlockState;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;

/**
 * Blocks and their states.
 */
public class NmsBlocks {

	/**
	 * @return the block's bounds, as they were last set, at the position
	 */
	public static AxisAlignedBB getBoundingBox(Block block, BlockPosition pos) {
		return new AxisAlignedBB(pos.getX() + block.B(), pos.getY() + block.D(), pos.getZ() + block.F(),
				pos.getX() + block.C(), pos.getY() + block.E(), pos.getZ() + block.G());
	}

	/**
	 * @return whether the block fills its whole space, like stone
	 */
	public static boolean isFullBlock(Block block) {
		return block.o();
	}

	/**
	 * @return the state with the id, as in a chunk section's block ids, or null if
	 *         there's none
	 */
	public static IBlockData getStateById(int id) {
		return Block.d.a(id);
	}

	/**
	 * @return the state's id, as in a chunk section's block ids
	 */
	public static int getStateId(IBlockData state) {
		return Block.d.b(state);
	}

	/**
	 * @return the state with the property set to its next value, e.g. an open door
	 *         closed
	 */
	public static <T extends Comparable<T>> IBlockData cycle(IBlockData state, IBlockState<T> property) {
		return state.a(property);
	}

	/**
	 * @param state the state of a block that has a tile entity
	 * @return a new tile entity for the block
	 */
	public static TileEntity createTileEntity(World world, IBlockData state) {
		Block block = state.getBlock();
		return ((IContainer) block).a(world, block.toLegacyData(state));
	}

	public static boolean isTrappedChest(BlockChest chest) {
		return chest.b == 1;
	}

	/**
	 * Sets the trapdoor's bounds to those of the state.
	 */
	public static void updateTrapdoorShape(BlockTrapdoor trapdoor, IBlockData state) {
		trapdoor.d(state);
	}

	/**
	 * Sets the door's bounds to those of its facing data.
	 *
	 * @param facingData the facing, half, hinge and whether it's open, worked
	 *                   out from both halves like VirtualBlockDoor does
	 */
	public static void updateDoorShape(BlockDoor door, int facingData) {
		try {
			new MethodWrapper<Void>(door, "k", int.class).call(facingData);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Couldn't update the bounding box of " + door, e);
		}
	}

	public static AxisAlignedBB getFenceGateBoundingBox(BlockFenceGate gate, BlockPosition pos, IBlockData state) {
		return gate.a(null, pos, state);
	}

	/**
	 * @return the property that tells the slab's variants apart, e.g. stone and
	 *         sandstone
	 */
	public static IBlockState<?> getSlabVariantProperty(BlockStepAbstract slab) {
		return slab.n();
	}

	/**
	 * @return the variant of the slab the item places
	 */
	public static Object getSlabVariant(BlockStepAbstract slab, ItemStack item) {
		return slab.a(item);
	}
}
