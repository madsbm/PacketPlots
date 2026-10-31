package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.BlockLeaves2;
import net.minecraft.server.v1_8_R3.BlockWood;
import net.minecraft.server.v1_8_R3.IBlockData;

public class VirtualBlockLeaves2 extends VirtualBlock<BlockLeaves2> {

	// vanilla's drop data for acacia and dark oak leaves is their sapling's, 4 and
	// 5, not the leaves' own, 0 and 1
	@Override
	public int getDropData(IBlockData iblockdata) {
		return ((BlockWood.EnumLogVariant) iblockdata.get(BlockLeaves2.VARIANT)).a() - 4;
	}

}
