package net.dashmc.plots.plot.data.blocks;

import java.util.Iterator;

import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.nms.NmsDirections;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ITileInventory;
import net.minecraft.server.v1_8_R3.InventoryLargeChest;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockAction;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntityChest;

public class VirtualChestBlock extends VirtualBlock<BlockChest> {

	@Override
	public boolean interact(BlockChest block, IVirtualWorld environment, BlockPosition blockposition,
			IBlockData iblockdata,
			EntityHuman entityhuman,
			EnumDirection enumdirection, float f, float f1, float f2) {
		ITileInventory itileinventory = getInventory(environment, blockposition, block);

		if (itileinventory != null) {
			PacketPlayOutBlockAction chestOpenPacket = new PacketPlayOutBlockAction(blockposition, block, 1, 1);
			environment.broadcast(chestOpenPacket);

			entityhuman.openContainer(itileinventory);
			NmsPlayers.awardStatistic(entityhuman,
					NmsBlocks.isTrappedChest(block) ? NmsPlayers.TRAPPED_CHEST_TRIGGERED : NmsPlayers.CHEST_OPENED);
		}

		return true;
	}

	@Override
	public void onPlace(BlockChest chest, IVirtualWorld world, BlockPosition blockposition,
			IBlockData iblockdata) {
		this.updateFacing(chest, world, blockposition, iblockdata);
		Iterator<EnumDirection> iterator = EnumDirection.EnumDirectionLimit.HORIZONTAL.iterator();

		while (iterator.hasNext()) {
			EnumDirection enumdirection = (EnumDirection) iterator.next();
			BlockPosition blockposition1 = blockposition.shift(enumdirection);
			IBlockData iblockdata1 = world.getType(blockposition1);

			if (iblockdata1.getBlock() == chest) {
				this.updateFacing(chest, world, blockposition1, iblockdata1);
			}
		}
	}

	@Override
	public void postPlace(BlockChest chest, IVirtualWorld environment, BlockPosition blockposition,
			IBlockData iblockdata, EntityLiving entityliving, ItemStack itemstack) {
		EnumDirection enumdirection = EnumDirection
				.fromType2(MathHelper.floor((double) (entityliving.yaw * 4.0F / 360.0F) + 0.5D) & 3).opposite();

		iblockdata = iblockdata.set(BlockChest.FACING, enumdirection);
		BlockPosition blockposition1 = blockposition.north();
		BlockPosition blockposition2 = blockposition.south();
		BlockPosition blockposition3 = blockposition.west();
		BlockPosition blockposition4 = blockposition.east();
		boolean flag = chest == environment.getType(blockposition1).getBlock();
		boolean flag1 = chest == environment.getType(blockposition2).getBlock();
		boolean flag2 = chest == environment.getType(blockposition3).getBlock();
		boolean flag3 = chest == environment.getType(blockposition4).getBlock();

		if (!flag && !flag1 && !flag2 && !flag3) {
			environment.setBlock(blockposition, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
		} else if (NmsDirections.getAxis(enumdirection) == EnumDirection.EnumAxis.X && (flag || flag1)) {
			if (flag) {
				environment.setBlock(blockposition1, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
			} else {
				environment.setBlock(blockposition2, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
			}

			environment.setBlock(blockposition, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
		} else if (NmsDirections.getAxis(enumdirection) == EnumDirection.EnumAxis.Z && (flag2 || flag3)) {
			if (flag2) {
				environment.setBlock(blockposition3, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
			} else {
				environment.setBlock(blockposition4, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
			}

			environment.setBlock(blockposition, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
		}

		if (itemstack.hasName()) {
			TileEntity tileentity = environment.getTileEntity(blockposition);

			if (tileentity instanceof TileEntityChest) {
				NmsTileEntities.setCustomName((TileEntityChest) tileentity, itemstack.getName());
			}
		}

	}

	@Override
	public boolean canPlace(BlockChest chest, IVirtualWorld environment, BlockPosition blockposition) {
		int i = 0;
		BlockPosition blockposition1 = blockposition.west();
		BlockPosition blockposition2 = blockposition.east();
		BlockPosition blockposition3 = blockposition.north();
		BlockPosition blockposition4 = blockposition.south();

		if (environment.getType(blockposition1).getBlock() == chest) {
			if (checkForSpace(chest, environment, blockposition1))
				return false;
			++i;
		}
		if (environment.getType(blockposition2).getBlock() == chest) {
			if (checkForSpace(chest, environment, blockposition2))
				return false;
			++i;
		}
		if (environment.getType(blockposition3).getBlock() == chest) {
			if (checkForSpace(chest, environment, blockposition3))
				return false;
			++i;
		}
		if (environment.getType(blockposition4).getBlock() == chest) {
			if (checkForSpace(chest, environment, blockposition4))
				return false;
			++i;
		}

		return i <= 1;
	}

	private boolean checkForSpace(BlockChest chest, IVirtualWorld environment, BlockPosition blockposition) {
		if (environment.getType(blockposition).getBlock() != chest) {
			return false;
		}

		Iterator<EnumDirection> iterator = EnumDirection.EnumDirectionLimit.HORIZONTAL.iterator();

		EnumDirection enumdirection;
		do {
			if (!iterator.hasNext())
				return false;
			enumdirection = (EnumDirection) iterator.next();
		} while (environment.getType(blockposition.shift(enumdirection)).getBlock() != chest);

		return true;
	}

	/**
	 * Turns the chest to face away from its neighbours, like BlockChest#e.
	 */
	public IBlockData updateFacing(BlockChest chest, IVirtualWorld world, BlockPosition blockposition,
			IBlockData iblockdata) {
		IBlockData iblockdata1 = world.getType(blockposition.north());
		IBlockData iblockdata2 = world.getType(blockposition.south());
		IBlockData iblockdata3 = world.getType(blockposition.west());
		IBlockData iblockdata4 = world.getType(blockposition.east());
		EnumDirection enumdirection = (EnumDirection) iblockdata.get(BlockChest.FACING);
		Block block = iblockdata1.getBlock();
		Block block1 = iblockdata2.getBlock();
		Block block2 = iblockdata3.getBlock();
		Block block3 = iblockdata4.getBlock();

		if (block != chest && block1 != chest) {
			boolean flag = NmsBlocks.isFullBlock(block);
			boolean flag1 = NmsBlocks.isFullBlock(block1);

			if (block2 == chest || block3 == chest) {
				BlockPosition blockposition1 = block2 == chest ? blockposition.west() : blockposition.east();
				IBlockData iblockdata5 = world.getType(blockposition1.north());
				IBlockData iblockdata6 = world.getType(blockposition1.south());

				enumdirection = EnumDirection.SOUTH;
				EnumDirection enumdirection1;

				if (block2 == chest) {
					enumdirection1 = (EnumDirection) iblockdata3.get(BlockChest.FACING);
				} else {
					enumdirection1 = (EnumDirection) iblockdata4.get(BlockChest.FACING);
				}

				if (enumdirection1 == EnumDirection.NORTH) {
					enumdirection = EnumDirection.NORTH;
				}

				Block block4 = iblockdata5.getBlock();
				Block block5 = iblockdata6.getBlock();

				if ((flag || NmsBlocks.isFullBlock(block4)) && !flag1 && !NmsBlocks.isFullBlock(block5)) {
					enumdirection = EnumDirection.SOUTH;
				}

				if ((flag1 || NmsBlocks.isFullBlock(block5)) && !flag && !NmsBlocks.isFullBlock(block4)) {
					enumdirection = EnumDirection.NORTH;
				}
			}
		} else {
			BlockPosition blockposition2 = block == chest ? blockposition.north() : blockposition.south();
			IBlockData iblockdata7 = world.getType(blockposition2.west());
			IBlockData iblockdata8 = world.getType(blockposition2.east());

			enumdirection = EnumDirection.EAST;
			EnumDirection enumdirection2;

			if (block == chest) {
				enumdirection2 = (EnumDirection) iblockdata1.get(BlockChest.FACING);
			} else {
				enumdirection2 = (EnumDirection) iblockdata2.get(BlockChest.FACING);
			}

			if (enumdirection2 == EnumDirection.WEST) {
				enumdirection = EnumDirection.WEST;
			}

			Block block6 = iblockdata7.getBlock();
			Block block7 = iblockdata8.getBlock();

			if ((NmsBlocks.isFullBlock(block2) || NmsBlocks.isFullBlock(block6)) && !NmsBlocks.isFullBlock(block3) && !NmsBlocks.isFullBlock(block7)) {
				enumdirection = EnumDirection.EAST;
			}

			if ((NmsBlocks.isFullBlock(block3) || NmsBlocks.isFullBlock(block7)) && !NmsBlocks.isFullBlock(block2) && !NmsBlocks.isFullBlock(block6)) {
				enumdirection = EnumDirection.WEST;
			}
		}

		iblockdata = iblockdata.set(BlockChest.FACING, enumdirection);
		world.setBlock(blockposition, iblockdata, IVirtualWorld.NOTIFY_AND_SEND);
		return iblockdata;
	}

	public ITileInventory getInventory(IVirtualWorld environment, BlockPosition blockposition, BlockChest chest) {
		TileEntity tileentity = environment.getTileEntity(blockposition);

		if (!(tileentity instanceof TileEntityChest)) {
			return null;
		} else {
			Object object = (TileEntityChest) tileentity;

			if (isBlockAboveOccluding(environment, blockposition)) {
				return null;
			} else {
				Iterator<EnumDirection> iterator = EnumDirection.EnumDirectionLimit.HORIZONTAL.iterator();

				while (iterator.hasNext()) {
					EnumDirection enumdirection = (EnumDirection) iterator.next();
					BlockPosition blockposition1 = blockposition.shift(enumdirection);
					Block block = environment.getType(blockposition1).getBlock();

					if (block == chest) {
						if (this.isBlockAboveOccluding(environment, blockposition1)) {
							return null;
						}

						TileEntity tileentity1 = environment.getTileEntity(blockposition1);

						if (tileentity1 instanceof TileEntityChest) {
							if (enumdirection != EnumDirection.WEST && enumdirection != EnumDirection.NORTH) {
								object = new InventoryLargeChest("container.chestDouble", (ITileInventory) object,
										(TileEntityChest) tileentity1);
							} else {
								object = new InventoryLargeChest("container.chestDouble", (TileEntityChest) tileentity1,
										(ITileInventory) object);
							}
						}
					}
				}

				return (ITileInventory) object;
			}
		}
	}

	private boolean isBlockAboveOccluding(IVirtualWorld environment, BlockPosition blockposition) {
		return environment.getType(blockposition.up()).getBlock().isOccluding();
	}

}
