package net.dashmc.plots.packets.interceptors;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.VirtualConnection;
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

	private final ChunkPacketRenderer renderer;

	@Override
	public boolean intercept(PacketPlayOutMapChunkBulk packet, VirtualConnection conn) {
		int[] xCoords = xCoordField.get(packet);
		int[] zCoords = zCoordField.get(packet);
		ChunkMap[] chunkMaps = chunkMapField.get(packet);

		for (int i = 0; i < xCoords.length; i++) {
			ChunkMap map = renderer.render(conn, xCoords[i], zCoords[i]);
			if (map != null)
				chunkMaps[i] = map;
		}

		return false;
	}

}
