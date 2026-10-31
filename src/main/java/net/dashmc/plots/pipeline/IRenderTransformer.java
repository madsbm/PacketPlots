package net.dashmc.plots.pipeline;

import net.dashmc.plots.plot.data.VirtualChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

/**
 * A step of a RenderPipeline, which changes the chunk map rendered from a
 * virtual chunk.
 */
@FunctionalInterface
public interface IRenderTransformer {
	/**
	 * @param metaStartIdx where the light and biome data start in the map, after
	 *                     the block ids
	 */
	void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx);
}
