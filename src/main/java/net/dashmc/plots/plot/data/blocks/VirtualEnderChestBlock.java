package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.nms.NmsInventories;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.plot.data.blocks.wrappers.EnderChestTileWrapper;
import net.minecraft.server.v1_8_R3.BlockEnderChest;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.InventoryEnderChest;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntityEnderChest;

public class VirtualEnderChestBlock extends VirtualBlock<BlockEnderChest> {

	@Override
	public boolean interact(BlockEnderChest block, IVirtualWorld environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
		InventoryEnderChest inventory = entityhuman.getEnderChest();
		TileEntity tile = environment.getTileEntity(blockposition);

		if (inventory == null)
			return true;
		if (!(tile instanceof TileEntityEnderChest))
			return true;

		if (environment.getType(blockposition.up()).getBlock().isOccluding())
			return true;

		TileEntityEnderChest enderChest = (TileEntityEnderChest) tile;
		EnderChestTileWrapper wrappedEnderChest = new EnderChestTileWrapper(environment, enderChest);

		NmsTileEntities.addPlayersUsing(enderChest, 1);
		NmsInventories.setEnderChest(inventory, wrappedEnderChest);
		entityhuman.openContainer(inventory);
		NmsPlayers.awardStatistic(entityhuman, NmsPlayers.ENDER_CHEST_OPENED);
		return true;

	}

}
