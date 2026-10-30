package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;

public class VirtualFenceGateBlock extends VirtualBlock<BlockFenceGate> {

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockFenceGate block, IEnvironment env, BlockPosition pos,
			IBlockData state) {

		Debug.log(block.getClass() + ", " + state.toString());

		return block.a(null, pos, state);
	}

	@Override
	public boolean interact(BlockFenceGate block, IEnvironment environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		if (((Boolean) iblockdata.get(BlockFenceGate.OPEN)).booleanValue()) {
			iblockdata = iblockdata.set(BlockFenceGate.OPEN, Boolean.valueOf(false));
			environment.setBlock(blockposition, iblockdata, 2);
		} else {
			EnumDirection enumdirection1 = EnumDirection.fromAngle((double) entityhuman.yaw);

			if (iblockdata.get(BlockFenceGate.FACING) == enumdirection1.opposite()) {
				iblockdata = iblockdata.set(BlockFenceGate.FACING, enumdirection1);
			}

			iblockdata = iblockdata.set(BlockFenceGate.OPEN, Boolean.valueOf(true));
			environment.setBlock(blockposition, iblockdata, 2);
		}

		int eventCode = ((Boolean) iblockdata.get(BlockFenceGate.OPEN)).booleanValue() ? 1003 : 1006;
		environment.broadcast(new PacketPlayOutWorldEvent(eventCode, blockposition, 0, false));
		return true;
	}

	@Override
	public Class<BlockFenceGate> getClazz() {
		return BlockFenceGate.class;
	}

}
