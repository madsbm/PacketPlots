package net.dashmc.plots.plot;

import java.util.Collection;

import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.plot.data.VirtualChunk;

/**
 * What the packet layer needs to render an environment for a viewer.
 */
public interface IRenderSource {
	/**
	 * @return the virtual chunk at the given chunk coordinates, or null if that
	 *         chunk isn't virtualized
	 */
	VirtualChunk getChunk(int chunkX, int chunkZ);

	Collection<VirtualChunk> getChunks();

	RenderPipeline getRenderPipeline();
}
