package net.dashmc.plots.plot.data.blocks;

import java.util.Arrays;
import java.util.Collection;

import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.BlockBag;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.TileEntity;

public class VirtualBlockDoor extends VirtualOpenableBlock<BlockDoor> {

	public VirtualBlockDoor() {
		super(BlockDoor.OPEN);
	}

	@Override
	public void onBlockHarvested(BlockDoor block, IVirtualWorld environment, BlockPosition pos, IBlockData data,
			BlockBag bag, TileEntity tile) {
		bag.add(block.getDropType(data, null, 0));
	}

	// the lower half keeps whether the door is open
	@Override
	protected BlockPosition getStatePosition(BlockPosition pos, IBlockData data) {
		return isLowerHalf(data) ? pos : pos.down();
	}

	// the client opens or closes both halves
	@Override
	public Collection<BlockPosition> getPredictedPositions(BlockDoor block, BlockPosition pos, IBlockData data) {
		return Arrays.asList(pos, isLowerHalf(data) ? pos.up() : pos.down());
	}

	private static boolean isLowerHalf(IBlockData data) {
		return data.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
	}

	@Override
	public void onRelatedUpdated(BlockDoor block, IVirtualWorld env, BlockPosition pos, BlockBag bag,
			IBlockData newBlockData) {
		if (newBlockData.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) {
			BlockPosition lower = pos.down();
			IBlockData door = env.getType(lower);

			if (door.getBlock() != block) {
				env.setBlock(pos, Blocks.AIR.getBlockData(), IVirtualWorld.NOTIFY_AND_SEND);
			}
		} else {
			BlockPosition upper = pos.up();
			IBlockData door = env.getType(upper);

			if (door.getBlock() != block) {
				env.setBlock(pos, Blocks.AIR.getBlockData(), IVirtualWorld.NOTIFY_AND_SEND);
				bag.add(block.getDropType(newBlockData, null, 0));
			}
		}
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockDoor block, IVirtualWorld env, BlockPosition pos,
			IBlockData state) {
		updateShape(block, env, pos);
		return super.getCollisionBoundingBox(block, env, pos, state);
	}

	private void updateShape(BlockDoor block, IVirtualWorld env, BlockPosition pos) {
		NmsBlocks.updateDoorShape(block, getFacingData(env, pos));
	}

	private static int getFacingData(IVirtualWorld env, BlockPosition blockposition) {
		IBlockData iblockdata = env.getType(blockposition);
		int i = iblockdata.getBlock().toLegacyData(iblockdata);
		boolean flag = (i & 8) != 0;
		IBlockData iblockdata1 = env.getType(blockposition.down());
		int j = iblockdata1.getBlock().toLegacyData(iblockdata1);
		int k = flag ? j : i;
		IBlockData iblockdata2 = env.getType(blockposition.up());
		int l = iblockdata2.getBlock().toLegacyData(iblockdata2);
		int i1 = flag ? i : l;
		boolean flag1 = (i1 & 1) != 0;
		boolean flag2 = (i1 & 2) != 0;

		return (k & 7) | (flag ? 8 : 0) | (flag1 ? 16 : 0) | (flag2 ? 32 : 0);
	}

}
