package net.dashmc.plots.plot;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;

/**
 * The blocks of a virtual environment: what block and item handlers need to
 * read and change them, and to show the changes to the environment's viewers.
 */
public interface IVirtualWorld extends IBlockAccess {
	boolean setBlock(BlockPosition pos, IBlockData data, int flags);

	boolean contains(BlockPosition pos);

	boolean contains(int x, int y, int z);

	/**
	 * @return the world this environment is virtualized in
	 */
	World getRealWorld();

	void broadcast(Packet<?> packet);

	default void broadcastTile(TileEntity tile) {
		broadcast(tile.getUpdatePacket());
	}
}
