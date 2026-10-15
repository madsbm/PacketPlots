package net.dashmc.plots.plot;

import java.util.List;

import org.bukkit.entity.Player;

import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;

public interface IVirtualWorld extends IBlockAccess {
	List<TileEntity> getTileEntities();

	boolean setBlock(BlockPosition pos, IBlockData data, int flags);

	boolean contains(BlockPosition pos);

	boolean contains(int x, int y, int z);

	World getRealWorld();

	void broadcast(Packet<?> packet);

	CuboidRegion getRegion();

	/**
	 * Not yet implemented - entities arent supported
	 * 
	 * @param bb
	 * @param entity
	 * @return
	 */
	boolean isNoOtherEntitiesInside(AxisAlignedBB bb, Entity entity);

	// https://github.com/Attano/Spigot-1.8/blob/9db48bc15e203179554b8d992ca6b0a528c8d300/net/minecraft/server/v1_8_R3/World.java#L2664
	boolean isBuildable(Block block, BlockPosition pos, boolean ignoreCollision, EnumDirection dir,
			Entity entity,
			ItemStack itemStack);

	boolean canPlace(BlockPosition pos, EnumDirection dir, ItemStack itemStack, EntityHuman player);

	void broadcastTile(TileEntity tile);

	void setTileEntity(BlockPosition blockPosition, TileEntity tileEntity);
}
