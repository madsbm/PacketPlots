package net.dashmc.plots.plot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import net.dashmc.plots.plot.CuboidEnvironment.InteractManager;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.minecraft.server.v1_8_R3.EntityHuman;

public interface IEnvironment extends IVirtualWorld, IRenderSource {

	HashSet<VirtualConnection> getConnections();

	HashMap<Integer, VirtualChunk> getVirtualChunks();

	UUID getOwnerUuid();

	default boolean isOwner(EntityHuman human) {
		return getOwnerUuid().equals(human.getUniqueID());
	}

	InteractManager getInteractManager();

	BlockBag getBlockBag();

	void close();

	void save();

}