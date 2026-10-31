package net.dashmc.plots.plot;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.plot.data.BlockBag;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.IBlockData;

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

	/**
	 * For other plugins, e.g. with a pipeline of
	 * net.dashmc.plots.api.pipeline.RenderPipelineFactory. Takes effect for the
	 * chunks sent from then on.
	 */
	void setRenderPipeline(RenderPipeline renderPipeline);

	/**
	 * @return the connections shown the environment: its owner's, unless they're
	 *         visiting another, and its visitors'
	 */
	Collection<VirtualConnection> getViewers();

	void addViewer(VirtualConnection connection);

	void removeViewer(VirtualConnection connection);

	/**
	 * Starts keeping what each block that changes was before, until
	 * {@link #stopRecordingChanges}, e.g. to undo a placement that's cancelled.
	 */
	void startRecordingChanges();

	/**
	 * @return the positions that changed since {@link #startRecordingChanges},
	 *         with what they were before, in the order they first changed
	 */
	Map<BlockPosition, IBlockData> stopRecordingChanges();

	/**
	 * Lets go of the environment's chunks and viewers. Use
	 * {@link EnvironmentManager#unload}, which sends its visitors back to their own
	 * environments and saves it first.
	 */
	void close();
}
