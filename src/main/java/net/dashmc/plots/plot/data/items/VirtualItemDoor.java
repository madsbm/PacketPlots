package net.dashmc.plots.plot.data.items;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemDoor;
import net.minecraft.server.v1_8_R3.ItemStack;

public class VirtualItemDoor extends VirtualItem<ItemDoor> {
	private static FieldAccess<Block> blockField;

	@Override
	public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment, BlockPosition pos,
			EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
		if (direction != EnumDirection.UP)
			return false;

		Debug.log("interact with called on item door. is border place? " + isBorderPlace);

		IBlockData ibd = isBorderPlace ? environment.getNmsWorld().getType(pos) : environment.getType(pos);
		Block currBlock = ibd.getBlock();
		if (!VirtualBlock.shouldRemainAt(currBlock, environment, pos)) {
			pos = pos.shift(direction);
		} else if (isBorderPlace)
			return false;

		if (!environment.canPlace(pos, direction, item, player))
			return false;

		Block toPlace;
		try {
			toPlace = (Block) blockField.get(((ItemDoor) item.getItem()));
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		if (!environment.isBuildable(toPlace, pos, false, direction, player, item))
			return false;

		placeDoor(environment, pos, EnumDirection.fromAngle((double) player.yaw), toPlace);
		--item.count;

		Debug.log("placed door @ " + pos);
		return true;
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/ItemDoor.java#L35
	private static void placeDoor(IEnvironment environment, BlockPosition pos, EnumDirection dir, Block block) {
		BlockPosition blockposition1 = pos.shift(dir.e());
		BlockPosition blockposition2 = pos.shift(dir.f());
		int i = (environment.getType(blockposition2).getBlock().isOccluding() ? 1 : 0)
				+ (environment.getType(blockposition2.up()).getBlock().isOccluding() ? 1 : 0);
		int j = (environment.getType(blockposition1).getBlock().isOccluding() ? 1 : 0)
				+ (environment.getType(blockposition1.up()).getBlock().isOccluding() ? 1 : 0);
		boolean flag = environment.getType(blockposition2).getBlock() == block
				|| environment.getType(blockposition2.up()).getBlock() == block;
		boolean flag1 = environment.getType(blockposition1).getBlock() == block
				|| environment.getType(blockposition1.up()).getBlock() == block;
		boolean flag2 = false;

		if (flag && !flag1 || j > i) {
			flag2 = true;
		}

		IBlockData iblockdata = block.getBlockData().set(BlockDoor.FACING, dir).set(BlockDoor.HINGE,
				flag2 ? BlockDoor.EnumDoorHinge.RIGHT : BlockDoor.EnumDoorHinge.LEFT);

		// Spigot start - update physics after the block multi place event
		environment.setBlock(pos, iblockdata.set(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER), 3);
		environment.setBlock(pos.up(), iblockdata.set(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), 3);
	}

	@Override
	public Class<ItemDoor> getClazz() {
		return ItemDoor.class;
	}

	static {
		try {
			blockField = new FieldAccess<>(ItemDoor.class, "a", Block.class);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}
}
