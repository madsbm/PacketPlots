package net.dashmc.plots.paste;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.helpers.MethodWrapper;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.NibbleArray;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;
import net.minecraft.server.v1_8_R3.WorldServer;

public class Paster {
	private static final NibbleArray fullyLit;

	public static boolean paste(VirtualChunk chunk, RenderPipeline pipeline, ChunkCoordIntPair at) {
		int atChunkX = at.x, atChunkZ = at.z;

		MethodWrapper<Void> packetSender = Utils.getRelatedPlayerPacketSender(atChunkX, atChunkZ,
				(WorldServer) chunk.getChunk().getWorld());
		if (packetSender == null)
			return false;

		ChunkMap map = pipeline.render(chunk);

		try {
			PacketPlayOutMapChunk packet = VirtualConnection.getRenderPacket(atChunkX, atChunkZ, map);
			packetSender.call(packet);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}

		Chunk nmsChunk = chunk.getChunk().getWorld().getChunkAt(atChunkX, atChunkZ);

		nmsChunk.f(true);
		nmsChunk.mustSave = true;

		int nonEmptyChunkSections = Integer.bitCount(map.b);
		int metaStartIdx = nonEmptyChunkSections * 2 * 16 * 16 * 16 + nonEmptyChunkSections * 16 * 16 * 8;

		int idPointer = 0;
		int sectionIndex = 0;

		char[] blockIds = new char[4096];
		for (int i = 0; i < metaStartIdx; i += 2) {
			char blockId = (char) ((map.a[i] & 0xFF) | ((map.a[i + 1] & 0xFF) << 8));
			blockIds[idPointer++] = blockId;

			if (idPointer == 4096) {
				for (; sectionIndex < 16; sectionIndex++) {
					if ((map.b & (1 << sectionIndex)) == 0)
						continue;
					break;
				}
				if (sectionIndex == 16)
					break;

				int chunkY = sectionIndex++;

				ChunkSection section = new ChunkSection(chunkY << 4, false, blockIds);
				nmsChunk.getSections()[chunkY] = section;

				section.a(fullyLit);
				section.b(fullyLit);

				blockIds = new char[4096];

				idPointer = 0;
			}
		}

		nmsChunk.initLighting();

		for (Packet<?> updatePacket : initializeTiles(chunk, nmsChunk)) {
			for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
				nmsPlayer.playerConnection.sendPacket(updatePacket);
			}
		}

		return true;
	}

	public static List<Packet<?>> initializeTiles(VirtualChunk vChunk, Chunk target) {
		List<Packet<?>> updatePackets = new LinkedList<>();
		IEnvironment env = vChunk.getEnvironment();

		int srcX = vChunk.getCoordPair().x << 4, srcZ = vChunk.getCoordPair().z << 4;
		int dstX = target.locX << 4, dstZ = target.locZ << 4;

		for (ChunkSection section : target.getSections()) {
			if (section == null)
				continue;

			int baseY = section.getYPosition();
			char[] ids = section.getIdArray();

			for (int idx = 0; idx < ids.length; idx++) {
				IBlockData ibd = Block.d.a(ids[idx]);
				if (ibd == null || !ibd.getBlock().isTileEntity())
					continue;

				int x = idx & 0xF;
				int y = baseY + ((idx >> 8) & 0xF);
				int z = (idx >> 4) & 0xF;

				TileEntity source = env.getTileEntity(new BlockPosition(srcX + x, y, srcZ + z));
				if (source == null)
					continue;

				BlockPosition dst = new BlockPosition(dstX + x, y, dstZ + z);
				NBTTagCompound nbt = new NBTTagCompound();
				source.b(nbt);
				nbt.setInt("x", dst.getX());
				nbt.setInt("y", dst.getY());
				nbt.setInt("z", dst.getZ());

				Block block = ibd.getBlock();
				TileEntity tile = ((IContainer) block).a(target.world, block.toLegacyData(ibd));
				tile.a(nbt);

				target.world.setTileEntity(dst, tile);
				updatePackets.add(tile.getUpdatePacket());
			}
		}

		return updatePackets;
	}

	static {
		byte[] arr = new byte[2048];
		Arrays.fill(arr, (byte) 255);

		fullyLit = new NibbleArray(arr);
	}
}
