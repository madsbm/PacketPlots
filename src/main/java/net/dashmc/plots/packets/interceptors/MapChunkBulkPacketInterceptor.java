package net.dashmc.plots.packets.interceptors;

import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Bukkit;

import net.dashmc.plots.PacketPlots;
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
	protected static final HashSet<Integer> coordPairs = new HashSet<>();
	private static FieldAccess<int[]> xCoordField;
	private static FieldAccess<int[]> zCoordField;
	private static FieldAccess<ChunkMap[]> chunkMapField;

	@Override
	public boolean intercept(PacketPlayOutMapChunkBulk packet, VirtualConnection conn) {
		IEnvironment env = conn.getEnvironment();

		try {
			LinkedList<Integer> indices = new LinkedList<>();

			int[] xCoords = (int[]) xCoordField.get(packet);
			int[] zCoords = (int[]) zCoordField.get(packet);
			ChunkMap[] chunkMaps = (ChunkMap[]) chunkMapField.get(packet);

			for (int i = 0; i < xCoords.length; i++) {
				int hash = Utils.getChunkCoordHash(xCoords[i], zCoords[i]);
				if (coordPairs.contains(hash)) {
					indices.add(i);
				}
			}

			for (int i : indices) {
				int hash = Utils.getChunkCoordHash(xCoords[i], zCoords[i]);

				VirtualChunk chunk = env.getVirtualChunks().get(hash);

				Bukkit.getScheduler().runTask(PacketPlots.getInstance(), () -> {
					chunk.sendTiles(conn.getPlayer());
				});

				try {
					ChunkMap chunkMap = env.getRenderPipeline().render(chunk);
					chunkMaps[i] = chunkMap;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}

		return false;
	}

	@Override
	public Class<PacketPlayOutMapChunkBulk> getClazz() {
		return PacketPlayOutMapChunkBulk.class;
	}

	public static void register() {
		VirtualConnection.registerInterceptor(new MapChunkBulkPacketInterceptor());
	}

	static {
		ChunkCoordIntPair[] pairs = PacketPlots.getPlotConfig().getRegion().getChunks();
		for (ChunkCoordIntPair pair : pairs) {
			coordPairs.add(pair.hashCode());
		}

		try {
			xCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "a", int[].class);
			zCoordField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "b", int[].class);
			chunkMapField = new FieldAccess<>(PacketPlayOutMapChunkBulk.class, "c", ChunkMap[].class);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}
}
