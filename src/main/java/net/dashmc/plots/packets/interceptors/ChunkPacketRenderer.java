package net.dashmc.plots.packets.interceptors;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.plot.IRenderSource;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

/**
 * Renders the virtual chunks of the chunk packets sent to a virtual connection,
 * for MapChunkPacketInterceptor and MapChunkBulkPacketInterceptor. Used on the
 * Netty thread.
 */
@RequiredArgsConstructor
public class ChunkPacketRenderer {
	// sends the chunks' tile entities, which happens on the main thread
	private final Executor mainThread;
	private final Logger logger;
	private final Debug debug;

	/**
	 * Renders the chunk if it's virtualized, and sends its tile entities after it.
	 *
	 * @return the chunk's map, or null to send the chunk as it really is: if it
	 *         isn't virtualized, or it failed to render
	 */
	public ChunkMap render(VirtualConnection conn, int chunkX, int chunkZ) {
		IRenderSource env = conn.getEnvironment();

		// null if the chunk isn't virtualized, or the environment closed or changed
		// while the packet was on its way
		VirtualChunk chunk = env.getChunk(chunkX, chunkZ);
		if (chunk == null)
			return null;

		// a chunk that fails to render is sent as it really is, rather than failing
		// the whole packet
		ChunkMap map;
		try {
			map = env.getRenderPipeline().render(chunk);
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Failed rendering virtual chunk " + chunkX + ", " + chunkZ + " for "
					+ conn.getPlayer().getName(), e);
			return null;
		}

		mainThread.execute(() -> {
			debug.log(() -> "sending tiles in chunk to player: " + chunk.getTileEntities().size());
			chunk.sendTiles(conn.getPlayer());
		});
		return map;
	}
}
