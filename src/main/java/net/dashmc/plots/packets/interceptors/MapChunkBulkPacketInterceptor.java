package net.dashmc.plots.packets.interceptors;

import java.util.concurrent.Executor;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.InterceptorRegistry;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunkBulk;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Map_Chunk_Bulk
@RequiredArgsConstructor
public class MapChunkBulkPacketInterceptor extends PacketInterceptor<PacketPlayOutMapChunkBulk> {
	private static final FieldAccess<int[]> xCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "a",
			int[].class);
	private static final FieldAccess<int[]> zCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "b",
			int[].class);
	private static final FieldAccess<ChunkMap[]> chunkMapField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class,
			"c", ChunkMap[].class);

	// sends the chunks' tile entities, which happens on the main thread
	private final Executor mainThread;

	@Override
	public boolean intercept(PacketPlayOutMapChunkBulk packet, VirtualConnection conn) {
		IEnvironment env = conn.getEnvironment();

		int[] xCoords = xCoordField.get(packet);
		int[] zCoords = zCoordField.get(packet);
		ChunkMap[] chunkMaps = chunkMapField.get(packet);

		for (int i = 0; i < xCoords.length; i++) {
			// null if the chunk isn't virtualized, or the environment closed or changed
			// while the packet was on its way
			VirtualChunk chunk = env.getChunk(xCoords[i], zCoords[i]);
			if (chunk == null)
				continue;

			mainThread.execute(() -> chunk.sendTiles(conn.getPlayer()));

			// a chunk that fails to render is sent as it really is, rather than failing
			// the whole packet
			try {
				chunkMaps[i] = env.getRenderPipeline().render(chunk);
			} catch (RuntimeException e) {
				Bukkit.getLogger().log(Level.SEVERE, "Failed rendering virtual chunk " + xCoords[i] + ", "
						+ zCoords[i] + " for " + conn.getPlayer().getName(), e);
			}
		}

		return false;
	}

	@Override
	public Class<PacketPlayOutMapChunkBulk> getClazz() {
		return PacketPlayOutMapChunkBulk.class;
	}

	public static void register(Executor mainThread) {
		InterceptorRegistry.register(new MapChunkBulkPacketInterceptor(mainThread));
	}
}
