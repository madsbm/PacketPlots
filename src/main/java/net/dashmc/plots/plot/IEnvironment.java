package net.dashmc.plots.plot;

import java.util.UUID;

import net.dashmc.plots.plot.data.BlockBag;
import net.minecraft.server.v1_8_R3.EntityHuman;

/**
 * A player's environment as a whole: its blocks, how it's rendered, who owns it
 * and who sees it. Code that only needs its blocks or its rendering takes an
 * IVirtualWorld or an IRenderSource.
 */
public interface IEnvironment extends IVirtualWorld, IRenderSource {
	UUID getOwnerUuid();

	default boolean isOwner(EntityHuman human) {
		return getOwnerUuid().equals(human.getUniqueID());
	}

	/**
	 * @return the items the owner got from the environment, such as broken blocks
	 */
	BlockBag getBlockBag();

	void addViewer(VirtualConnection connection);

	void removeViewer(VirtualConnection connection);

	/**
	 * Sends the environment's visitors back to their own environments. Use
	 * {@link EnvironmentManager#unload}, which saves the environment first.
	 */
	void close();
}
