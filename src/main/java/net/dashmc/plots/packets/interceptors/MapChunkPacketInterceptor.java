package net.dashmc.plots.packets.interceptors;

import org.bukkit.Bukkit;

import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Chunk_Data
public class MapChunkPacketInterceptor extends PacketInterceptor<PacketPlayOutMapChunk> {
	private static FieldAccess<Integer> xCoordField;
	private static FieldAccess<Integer> zCoordField;
	private static FieldAccess<ChunkMap> chunkMapField;

	@Override
	public boolean intercept(PacketPlayOutMapChunk packet, VirtualConnection conn) {
		IEnvironment environment = conn.getEnvironment();

		try {
			int xCoord = xCoordField.get(packet);
			int zCoord = zCoordField.get(packet);

			int hash = Utils.getChunkCoordHash(xCoord, zCoord);
			if (MapChunkBulkPacketInterceptor.coordPairs.contains(hash)) {
				VirtualChunk chunk = environment.getVirtualChunks().get(hash);
				chunkMapField.set(packet, environment.getRenderPipeline().render(chunk));

				Bukkit.getScheduler().runTask(PacketPlots.getInstance(), () -> {
					chunk.sendTiles(conn.getPlayer());
				});
			}

		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public Class<PacketPlayOutMapChunk> getClazz() {
		return PacketPlayOutMapChunk.class;
	}

	public static void register() {
		VirtualConnection.registerInterceptor(new MapChunkPacketInterceptor());
	}

	static {
		try {
			xCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "a", int.class);
			zCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "b", int.class);
			chunkMapField = new FieldAccess<>(PacketPlayOutMapChunk.class, "c", ChunkMap.class);
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}
}
