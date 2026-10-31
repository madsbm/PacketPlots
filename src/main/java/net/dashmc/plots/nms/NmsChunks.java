package net.dashmc.plots.nms;

import java.lang.reflect.Method;

import net.dashmc.plots.utils.helpers.MethodWrapper;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.NibbleArray;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;
import net.minecraft.server.v1_8_R3.PlayerChunkMap;
import net.minecraft.server.v1_8_R3.WorldServer;

/**
 * Chunks, their sections and the maps of them sent to players.
 */
public class NmsChunks {
	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerChunkMap.java#L88
	private static final Method getOrCreatePlayerChunk = findGetOrCreatePlayerChunk();

	/**
	 * @return a key only this chunk has, unlike ChunkCoordIntPair#hashCode, which
	 *         thousands of chunks in a world share
	 */
	public static long getKey(int chunkX, int chunkZ) {
		return ChunkCoordIntPair.a(chunkX, chunkZ);
	}

	public static ChunkCoordIntPair getCoords(Chunk chunk) {
		return chunk.j();
	}

	/**
	 * Marks the chunk changed, so it's saved.
	 */
	public static void setModified(Chunk chunk, boolean to) {
		chunk.f(to);
	}

	public static void setBlockLight(ChunkSection section, NibbleArray light) {
		section.a(light);
	}

	public static void setSkyLight(ChunkSection section, NibbleArray light) {
		section.b(light);
	}

	/**
	 * @return the map's data: the block ids of its sections, 2 bytes each, then
	 *         their light and the biomes
	 */
	public static byte[] getData(ChunkMap map) {
		return map.a;
	}

	public static void setData(ChunkMap map, byte[] data) {
		map.a = data;
	}

	/**
	 * @return a bit for each section of the chunk the map has, from the bottom
	 */
	public static int getSectionMask(ChunkMap map) {
		return map.b;
	}

	public static void setSectionMask(ChunkMap map, int mask) {
		map.b = mask;
	}

	/**
	 * @return a sender of packets to the players who have the chunk loaded, or null
	 *         if none do
	 */
	public static MethodWrapper<Void> getViewerPacketSender(WorldServer world, int chunkX, int chunkZ) {
		PlayerChunkMap playerChunkMap = world.getPlayerChunkMap();
		if (!playerChunkMap.isChunkInUse(chunkX, chunkZ))
			return null;

		try {
			Object playerChunk = getOrCreatePlayerChunk.invoke(playerChunkMap, chunkX, chunkZ, true);

			Method packetSender = playerChunk.getClass().getMethod("a", Packet.class);
			packetSender.setAccessible(true);
			return new MethodWrapper<Void>(playerChunk, packetSender);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Couldn't get the packet sender of chunk " + chunkX + ", " + chunkZ, e);
		}
	}

	private static Method findGetOrCreatePlayerChunk() {
		try {
			Method method = PlayerChunkMap.class.getDeclaredMethod("a", int.class, int.class, boolean.class);
			method.setAccessible(true);
			return method;
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("PlayerChunkMap has no method a(int, int, boolean)", e);
		}
	}
}
