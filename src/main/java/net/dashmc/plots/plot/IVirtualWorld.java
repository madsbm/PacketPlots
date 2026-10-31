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
	/**
	 * A flag of {@link #setBlock}: the neighbouring blocks are updated, e.g. so
	 * the other half of a broken door breaks too. Vanilla's 1.
	 */
	int NOTIFY_NEIGHBOURS = 1;

	/**
	 * A flag of {@link #setBlock}: the change is sent to the environment's
	 * viewers. Vanilla's 2.
	 */
	int SEND_TO_VIEWERS = 2;

	/**
	 * Sets the block like World#setTypeAndData, so callers pass the same flags as
	 * vanilla: 3 for both, 2 to only send the change.
	 *
	 * @param flags {@link #NOTIFY_NEIGHBOURS} and {@link #SEND_TO_VIEWERS}
	 * @return whether the block changed
	 */
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
