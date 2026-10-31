package net.dashmc.plots.plot.data.items;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsNbt;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.plot.interaction.PlacementRules;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemBlock;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;

@RequiredArgsConstructor
public class VirtualItemBlock extends VirtualItem<ItemBlock> {
	private final VirtualBlockRegistry blocks;
	private final PlacementRules placement;
	private final Debug debug;

	@Override
	public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment, BlockPosition clicked,
			EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
		BlockPosition pos = placement.getPlacementPosition(environment, clicked, direction, isBorderPlace);
		if (pos == null)
			return false;

		Block toPlace = ((ItemBlock) item.getItem()).d();
		debug.log(() -> "Trying to place " + toPlace + " at " + pos + " (clicked " + clicked + ")");

		if (item.count == 0)
			return false;

		if (!placement.canPlace(environment, pos, direction, item, player))
			return false;

		if (!placement.isBuildable(environment, toPlace, pos, false, direction, player, item))
			return false;

		int data = item.getItem().filterData(item.getData());
		IBlockData placedBlockData = toPlace.getPlacedState(null, pos, direction, cX, cY, cZ, data, player);
		if (!environment.setBlock(pos, placedBlockData, IVirtualWorld.NOTIFY_AND_SEND))
			return false;

		IBlockData blockData = environment.getType(pos);
		if (blockData.getBlock() == toPlace) {
			initializeBlock(environment, pos, item);
			blocks.handlerOf(toPlace).postPlace(toPlace, environment, pos, blockData, player, item);
		}
		item.count--;

		return true;
	}

	// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/ItemBlock.java
	public static boolean initializeBlock(IVirtualWorld env, BlockPosition pos, ItemStack itemStack) {
		if (!(itemStack.hasTag() && itemStack.getTag().hasKeyOfType("BlockEntityTag", 10)))
			return false;
		TileEntity tile = env.getTileEntity(pos);
		if (tile == null)
			return false;

		NBTTagCompound compound = new NBTTagCompound();
		NBTTagCompound nbttagcompound1 = (NBTTagCompound) compound.clone();
		NmsTileEntities.save(tile, compound);

		NBTTagCompound nbttagcompound2 = (NBTTagCompound) itemStack.getTag().get("BlockEntityTag");

		NmsNbt.merge(compound, nbttagcompound2);
		compound.setInt("x", pos.getX());
		compound.setInt("y", pos.getY());
		compound.setInt("z", pos.getZ());

		if (compound.equals(nbttagcompound1))
			return false;

		NmsTileEntities.load(tile, compound);
		tile.update();
		return true;
	}

}
