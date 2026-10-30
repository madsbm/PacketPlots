package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockTrapdoor;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;

public class VirtualBlockTrapDoor extends VirtualBlock<BlockTrapdoor> {

	@Override
	public Class<BlockTrapdoor> getClazz() {
		return BlockTrapdoor.class;
	}

	public boolean interact(BlockTrapdoor block, IEnvironment environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {

		iblockdata = iblockdata.a(BlockTrapdoor.OPEN);
		environment.setBlock(blockposition, iblockdata, 2);

		int eventCode = iblockdata.get(BlockTrapdoor.OPEN).booleanValue() ? 1003 : 1006;
		environment.broadcast(new PacketPlayOutWorldEvent(eventCode, blockposition, 0, false));

		return true;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockTrapdoor block, IEnvironment env, BlockPosition pos,
			IBlockData state) {
		this.updateShape(block, env, pos);
		return super.getCollisionBoundingBox(block, env, pos, state);
	}

	private void updateShape(BlockTrapdoor block, IEnvironment env, BlockPosition pos) {
		block.d(env.getType(pos));
	}

}
