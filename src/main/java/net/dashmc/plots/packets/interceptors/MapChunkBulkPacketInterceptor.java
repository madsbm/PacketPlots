package net.dashmc.plots.packets.interceptors;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.packets.InterceptorRegistry;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunkBulk;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Map_Chunk_Bulk
public class MapChunkBulkPacketInterceptor extends PacketInterceptor<PacketPlayOutMapChunkBulk> {
	protected static final HashSet<Long> coordPairs = new HashSet<>();
	private static final FieldAccess<int[]> xCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "a",
			int[].class);
	private static final FieldAccess<int[]> zCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "b",
			int[].class);
	private static final FieldAccess<ChunkMap[]> chunkMapField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class,
			"c", ChunkMap[].class);

	@Override
	public boolean intercept(PacketPlayOutMapChunkBulk packet, VirtualConnection conn) {
		IEnvironment env = conn.getEnvironment();

		LinkedList<Integer> indices = new LinkedList<>();

		int[] xCoords = xCoordField.get(packet);
		int[] zCoords = zCoordField.get(packet);
		ChunkMap[] chunkMaps = chunkMapField.get(packet);

		for (int i = 0; i < xCoords.length; i++) {
			long key = Utils.getChunkKey(xCoords[i], zCoords[i]);
			if (coordPairs.contains(key)) {
				indices.add(i);
			}
		}

		for (int i : indices) {
			// null if the environment closed or changed while the packet was on its way
			VirtualChunk chunk = env.getChunk(xCoords[i], zCoords[i]);
			if (chunk == null)
				continue;

			Bukkit.getScheduler().runTask(PacketPlots.getInstance(), () -> {
				chunk.sendTiles(conn.getPlayer());
			});

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

	public static void register() {
		InterceptorRegistry.register(new MapChunkBulkPacketInterceptor());
	}

	static {
		ChunkCoordIntPair[] pairs = PacketPlots.getPlotConfig().getRegion().getChunks();
		for (ChunkCoordIntPair pair : pairs) {
			coordPairs.add(Utils.getChunkKey(pair.x, pair.z));
		}
	}
}
