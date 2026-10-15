package net.dashmc.plots.plot;

import java.util.Collection;

import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.plot.data.VirtualChunk;

public interface IRenderSource {
	VirtualChunk getChunk(int chunkX, int chunkZ);

	Collection<VirtualChunk> getChunks();

	RenderPipeline getRenderPipeline();
}
