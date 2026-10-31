package net.dashmc.plots.plot;

import java.util.List;
import java.util.UUID;

import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.TileEntity;

public interface IEnvironment extends IVirtualWorld, IRenderSource {
	UUID getOwnerUuid();

	default boolean isOwner(EntityHuman human) {
		return getOwnerUuid().equals(human.getUniqueID());
	}

	BlockBag getBlockBag();

	/**
	 * @return what calls the events of what happens in the environment
	 */
	EventDispatcher getEvents();

	/**
	 * @return how the environment's blocks behave
	 */
	VirtualBlockRegistry getBlockHandlers();

	/**
	 * @return where to log what happens in the environment, when debugging
	 */
	Debug getDebug();

	void addViewer(VirtualConnection connection);

	void removeViewer(VirtualConnection connection);

	// only VirtualChunk uses these; the tile methods go with the duplicate tile list
	CuboidRegion getRegion();

	List<TileEntity> getTileEntities();

	void setTileEntity(BlockPosition pos, TileEntity tile);

	/**
	 * Sends the environment's visitors back to their own environments. Use
	 * {@link EnvironmentManager#unload}, which saves the environment first.
	 */
	void close();
}
