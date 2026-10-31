package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.BlockAir;
import net.minecraft.server.v1_8_R3.BlockPosition;

public class VirtualBlockAir extends VirtualBlock<BlockAir> {

	@Override
	public boolean shouldRemainAt(IVirtualWorld env, BlockPosition pos) {
		return true;
	}

}
