package net.dashmc.plots.packets.interceptors;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.packets.extensions.VirtualMapChunkPacket;
import net.dashmc.plots.plot.VirtualConnection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Chunk_Data
@RequiredArgsConstructor
public class MapChunkPacketInterceptor extends PacketInterceptor<PacketPlayOutMapChunk> {
	private final ChunkPacketRenderer renderer;

	@Override
	public boolean intercept(PacketPlayOutMapChunk packet, VirtualConnection conn) {
		VirtualMapChunkPacket chunkPacket = new VirtualMapChunkPacket(packet);

		ChunkMap map = renderer.render(conn, chunkPacket.getChunkX(), chunkPacket.getChunkZ());
		if (map != null) {
			chunkPacket.setChunkMap(map);
			return false;
		}

		// a packet of a virtual chunk that was to be rendered here has nothing to send,
		// so it's dropped
		return chunkPacket.getChunkMap() == null;
	}

}
