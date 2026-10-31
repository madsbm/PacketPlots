package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;

public class VirtualFenceGateBlock extends VirtualOpenableBlock<BlockFenceGate> {
	private final Debug debug;

	public VirtualFenceGateBlock(Debug debug) {
		super(BlockFenceGate.OPEN);
		this.debug = debug;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockFenceGate block, IVirtualWorld env, BlockPosition pos,
			IBlockData state) {

		debug.log(() -> block.getClass() + ", " + state.toString());

		return NmsBlocks.getFenceGateBoundingBox(block, pos, state);
	}

	// a gate opens away from the player
	@Override
	protected IBlockData toggle(IBlockData state, EntityHuman human) {
		if (state.get(BlockFenceGate.OPEN))
			return state.set(BlockFenceGate.OPEN, false);

		EnumDirection facing = EnumDirection.fromAngle((double) human.yaw);
		if (state.get(BlockFenceGate.FACING) == facing.opposite())
			state = state.set(BlockFenceGate.FACING, facing);

		return state.set(BlockFenceGate.OPEN, true);
	}

}
