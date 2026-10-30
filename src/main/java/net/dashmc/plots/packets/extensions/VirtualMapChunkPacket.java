package net.dashmc.plots.packets.extensions;

import lombok.Getter;
import net.dashmc.plots.packets.IPacketExtension;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

public class VirtualMapChunkPacket implements IPacketExtension<PacketPlayOutMapChunk> {
	private static final FieldAccess<Integer> xCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "a",
			int.class);
	private static final FieldAccess<Integer> zCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "b",
			int.class);
	private static final FieldAccess<ChunkMap> chunkMapField = new FieldAccess<>(PacketPlayOutMapChunk.class, "c",
			ChunkMap.class);

	@Getter
	private final PacketPlayOutMapChunk packet;

	/**
	 * @param chunkMap the chunk's data, or null if MapChunkPacketInterceptor will
	 *                 render it as the packet is sent
	 */
	public VirtualMapChunkPacket(int chunkX, int chunkZ, ChunkMap chunkMap) {
		packet = new PacketPlayOutMapChunk();

		xCoordField.set(packet, chunkX);
		zCoordField.set(packet, chunkZ);

		if (chunkMap != null)
			chunkMapField.set(packet, chunkMap);
	}
}
