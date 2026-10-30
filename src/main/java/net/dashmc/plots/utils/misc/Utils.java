package net.dashmc.plots.utils.misc;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;

import net.dashmc.plots.utils.helpers.MethodWrapper;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PlayerChunkMap;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;

public class Utils {
	// chunk utils
	public static int getChunkCoordHash(int x, int z) {
		int xHash = 1664525 * x + 1013904223;
		int zHash = 1664525 * (z ^ -559038737) + 1013904223;
		return xHash ^ zHash;
	}

	public static int getChunkCoordHash(BlockPosition pos) {
		return getChunkCoordHash(pos.getX() >> 4, pos.getZ() >> 4);
	}

	// coord utils
	public static Location convertPosToLoc(World world, BlockPosition pos) {
		CraftWorld craftWorld = world.getWorld();
		return convertPosToLoc(craftWorld, pos);
	}

	public static Location convertPosToLoc(CraftWorld craftWorld, BlockPosition pos) {
		return convertPosToLoc((org.bukkit.World) craftWorld, pos);
	}

	public static Location convertPosToLoc(org.bukkit.World world, BlockPosition pos) {
		return new Location(world, pos.getX(), pos.getY(), pos.getZ());
	}

	public static BlockPosition convertLocToPos(Location loc) {
		return new BlockPosition(loc.getX(), loc.getY(), loc.getZ());
	}

	// nms

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerChunkMap.java#L88
	private static final Method getOrCreatePlayerChunk = findGetOrCreatePlayerChunk();

	/**
	 * @return a sender of packets to the players who have the chunk loaded, or null
	 *         if none do
	 */
	public static MethodWrapper<Void> getRelatedPlayerPacketSender(int chunkX, int chunkZ, WorldServer server) {
		PlayerChunkMap playerChunkMap = server.getPlayerChunkMap();
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
