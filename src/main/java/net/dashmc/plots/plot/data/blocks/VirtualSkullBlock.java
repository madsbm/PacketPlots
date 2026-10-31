package net.dashmc.plots.plot.data.blocks;

import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockSkull;
import net.minecraft.server.v1_8_R3.GameProfileSerializer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntitySkull;

public class VirtualSkullBlock extends VirtualBlock<BlockSkull> {

	// the skull's type and owner are in its tile entity, which is gone from the
	// environment by now
	@Override
	public void onBlockHarvested(BlockSkull block, IVirtualWorld environment, BlockPosition pos, IBlockData data,
			BlockBag bag, TileEntity tile) {
		if (!(tile instanceof TileEntitySkull)) {
			bag.add(new ItemStack(Items.SKULL, 1, getDropData(data)));
			return;
		}

		TileEntitySkull skullTile = (TileEntitySkull) tile;
		ItemStack skullItem = new ItemStack(Items.SKULL, 1, skullTile.getSkullType());
		if (skullTile.getSkullType() == 3 && skullTile.getGameProfile() != null) {
			skullItem.setTag(new NBTTagCompound());
			NBTTagCompound compound = new NBTTagCompound();
			GameProfileSerializer.serialize(compound, skullTile.getGameProfile());
			skullItem.getTag().set("SkullOwner", compound);
		}

		bag.add(skullItem);
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(BlockSkull block, IVirtualWorld env, BlockPosition pos,
			IBlockData state) {
		block.updateShape(env, pos);
		return super.getCollisionBoundingBox(block, env, pos, state);
	}

	public int getDropData(IVirtualWorld environment, BlockPosition blockposition) {
		TileEntity tileentity = environment.getTileEntity(blockposition);

		return tileentity instanceof TileEntitySkull ? ((TileEntitySkull) tileentity).getSkullType()
				: super.getDropData(environment, blockposition);
	}

}
