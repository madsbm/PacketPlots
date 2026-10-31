package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.IVirtualWorld;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockTrapdoor;
import net.minecraft.server.v1_8_R3.IBlockData;

public class VirtualBlockTrapDoor extends VirtualOpenableBlock<BlockTrapdoor> {

	public VirtualBlockTrapDoor() {
		super(BlockTrapdoor.OPEN);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockTrapdoor block, IVirtualWorld env, BlockPosition pos,
			IBlockData state) {
		this.updateShape(block, env, pos);
		return super.getCollisionBoundingBox(block, env, pos, state);
	}

	private void updateShape(BlockTrapdoor block, IVirtualWorld env, BlockPosition pos) {
		block.d(env.getType(pos));
	}

}
