package net.dashmc.plots.packets.interceptors;

import java.util.concurrent.Executor;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.InterceptorRegistry;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

// https://minecraft.wiki/w/Protocol?oldid=2772100#Chunk_Data
@RequiredArgsConstructor
public class MapChunkPacketInterceptor extends PacketInterceptor<PacketPlayOutMapChunk> {
	private static final FieldAccess<Integer> xCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "a",
			int.class);
	private static final FieldAccess<Integer> zCoordField = new FieldAccess<>(PacketPlayOutMapChunk.class, "b",
			int.class);
	private static final FieldAccess<ChunkMap> chunkMapField = new FieldAccess<>(PacketPlayOutMapChunk.class, "c",
			ChunkMap.class);

	// sends the chunk's tile entities, which happens on the main thread
	private final Executor mainThread;

	@Override
	public boolean intercept(PacketPlayOutMapChunk packet, VirtualConnection conn) {
		IEnvironment environment = conn.getEnvironment();

		// null if the chunk isn't virtualized, or the environment closed or changed
		// while the packet was on its way
		VirtualChunk chunk = environment.getChunk(xCoordField.get(packet), zCoordField.get(packet));
		if (chunk == null)
			return false;

		chunkMapField.set(packet, environment.getRenderPipeline().render(chunk));

		mainThread.execute(() -> chunk.sendTiles(conn.getPlayer()));

		return false;
	}

	@Override
	public Class<PacketPlayOutMapChunk> getClazz() {
		return PacketPlayOutMapChunk.class;
	}

	public static void register(Executor mainThread) {
		InterceptorRegistry.register(new MapChunkPacketInterceptor(mainThread));
	}
}
