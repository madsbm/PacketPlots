package net.dashmc.plots.plot.data.items;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.plot.interaction.PlacementRules;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockSkull;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.GameProfileSerializer;
import net.minecraft.server.v1_8_R3.ItemSkull;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.MathHelper;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.TileEntitySkull;

@RequiredArgsConstructor
public class VirtualItemSkull extends VirtualItem<ItemSkull> {
	private final PlacementRules placement;

	@Override
	public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment, BlockPosition clicked,
			EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
		if (direction == EnumDirection.DOWN)
			return false;

		BlockPosition pos = placement.getPlacementPosition(environment, clicked, direction, isBorderPlace);
		if (pos == null)
			return false;

		// placed against the clicked block, which has to be solid
		boolean againstClicked = !pos.equals(clicked);
		if (againstClicked && !isBorderPlace
				&& !environment.getType(clicked).getBlock().getMaterial().isBuildable())
			return false;

		if (!placement.canPlace(environment, pos, direction, item, player))
			return false;
		if (!environment.getType(pos).getBlock().getMaterial().isReplaceable())
			return false;

		environment.setBlock(pos, Blocks.SKULL.getBlockData().set(BlockSkull.FACING, direction),
				IVirtualWorld.NOTIFY_AND_SEND);
		item.count--;

		int i = 0;
		if (direction == EnumDirection.UP) {
			i = MathHelper.floor((double) (player.yaw * 16.0F / 360.0F) + 0.5D) & 15;
		}

		TileEntity tile = environment.getTileEntity(pos);
		if (!(tile instanceof TileEntitySkull))
			return true;

		TileEntitySkull skull = (TileEntitySkull) tile;
		if (item.getData() == 3) {
			GameProfile profile = null;
			if (item.hasTag()) {
				NBTTagCompound nbt = item.getTag();
				if (nbt.hasKeyOfType("SkullOwner", 10))
					profile = GameProfileSerializer.deserialize(nbt.getCompound("SkullOwner"));
				else if (nbt.hasKeyOfType("SkullOwner", 8) && nbt.getString("SkullOwner").length() > 0)
					profile = new GameProfile((UUID) null, nbt.getString("SkullOwner"));
			}

			skull.setGameProfile(profile);
		} else {
			skull.setSkullType(item.getData());
		}

		skull.setRotation(i);
		return true;
	}

}
