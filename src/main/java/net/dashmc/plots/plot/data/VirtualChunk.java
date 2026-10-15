package net.dashmc.plots.plot.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import org.bukkit.Bukkit;
import lombok.Getter;
import net.dashmc.plots.data.IDataHolder;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.helpers.NBTHelper;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockContainer;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkProviderServer;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.NBTReadLimiter;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;

public class VirtualChunk implements IDataHolder {
	private static Field xCoordField;
	private static Field zCoordField;
	private static Field chunkMapField;

	private static Field nonEmptyBlockCountField;

	private @Getter ChunkCoordIntPair coordPair;

	private Chunk chunk;
	private @Getter IEnvironment environment;
	private World world;
	private @Getter Section[] sections = new Section[16];
	private Map<BlockPosition, TileEntity> tileEntities = new HashMap<>();
	private @Getter char sectionMask = 0;

	public VirtualChunk(IEnvironment environment, ChunkCoordIntPair coordPair) {
		this.environment = environment;
		this.coordPair = coordPair;
		this.world = environment.getNmsWorld();

		WorldServer world = (WorldServer) this.world;
		this.chunk = world.chunkProviderServer.getChunkAt(coordPair.x, coordPair.z);

		this.sectionMask = environment.getRegion().getSectionMask();
		ChunkSection[] real = chunk.getSections();
		for (byte i = 0; i < 16; i++) {
			if ((sectionMask & 1 << i) == 0)
				continue;

			sections[i] = real[i] != null ? new Section(real[i]) : new Section(i);
		}

		Debug.log("vChunk sections: " + Arrays.toString(chunk.getSections()));
		Debug.log("tiles: " + chunk.getTileEntities());

		for (Map.Entry<BlockPosition, TileEntity> entry : chunk.getTileEntities().entrySet()) {
			setBlock(entry.getKey(), Blocks.AIR.getBlockData());
		}
	}

	public VirtualChunk(IEnvironment environment, DataInputStream stream) throws IOException {
		this.environment = environment;
		this.world = environment.getNmsWorld();
		deserialize(stream);
	}

	public boolean isSectionSet(byte sectionIndex) {
		return (sectionMask & (1 << sectionIndex)) > 0;
	}

	public Chunk getChunk() {
		if (this.chunk != null)
			return this.chunk;
		// try to load it again if it is not loaded!
		this.chunk = world.getChunkAt(coordPair.x, coordPair.z);
		return this.chunk;
	}

	public boolean setBlock(BlockPosition pos, IBlockData blockData) {
		byte relX = (byte) (pos.getX() & 15);
		byte relZ = (byte) (pos.getZ() & 15);
		byte yPos = (byte) (pos.getY() >> 4);

		if (!environment.isValidLocation(pos)) {
			Bukkit.getLogger().warning(
					"setBlock called on VirtualChunk even though the reference position was not in the virtualized chunk.");
			return false;
		}

		IBlockData currentBlock = getBlockData(pos);
		if (currentBlock == blockData)
			return false;

		Block currBlock = currentBlock.getBlock();
		Block newBlock = blockData.getBlock();
		Section section = sections[yPos];
		if (section == null) {
			if (newBlock == Blocks.AIR)
				return false;

			ChunkSection real = getChunk().getSections()[yPos];
			section = real != null ? new Section(real) : new Section(yPos);
			sections[yPos] = section;
			sectionMask |= 1 << yPos;
		}
		section.setBlock(relX, (byte) (pos.getY() & 15), relZ, blockData);

		if (currBlock != newBlock) {
			if (!this.world.isClientSide) {
				// block1.remove(this.world, pos, currentBlock);
			}
		}

		if (section.getBlock(relX, (byte) (pos.getY() & 15), relZ) != newBlock)
			return false;

		if (currBlock instanceof IContainer) {
			TileEntity tileEntity = getTileEntity(pos, EnumTileEntityState.CHECK);
			if (tileEntity != null)
				tileEntity.E();
		}

		if (currBlock != newBlock && newBlock instanceof BlockContainer) {
			VirtualBlock.onPlace(environment, pos, blockData);
		}

		if (newBlock instanceof IContainer) {
			TileEntity tileEntity = getTileEntity(pos, EnumTileEntityState.CHECK);
			if (tileEntity == null) {
				tileEntity = ((IContainer) newBlock).a(world, newBlock.toLegacyData(blockData));
				environment.setTileEntity(pos, tileEntity);
			}

			if (tileEntity != null)
				tileEntity.E();
		}

		return true;
	}

	public void sendTiles(EntityPlayer to) {
		Debug.log("sending tiles in chunk to player: " + tileEntities.size());
		Iterator<TileEntity> tiles = tileEntities.values().iterator();
		while (tiles.hasNext()) {
			Packet<?> updatePacket = tiles.next().getUpdatePacket();
			if (updatePacket == null)
				continue;

			to.playerConnection.sendPacket(updatePacket);
		}
	}

	public void setTileEntity(BlockPosition pos, TileEntity entity) {
		entity.a(pos);
		if (getBlockData(pos).getBlock() instanceof IContainer) {
			if (tileEntities.containsKey(pos)) {
				tileEntities.get(pos).y();
			}

			entity.D();
			this.tileEntities.put(pos, entity);
		}
	}

	@Override
	public void deserialize(DataInputStream stream) throws IOException {
		IEnvironment environment = getEnvironment();
		this.world = environment.getNmsWorld();

		this.coordPair = new ChunkCoordIntPair(stream.readInt(), stream.readInt());

		Debug.log("Deserializing chunk @ " + coordPair.x + " , " + coordPair.z);

		ChunkProviderServer cps = (ChunkProviderServer) world.N();
		if (cps.isChunkLoaded(coordPair.x, coordPair.z)) {
			this.chunk = world.getChunkAt(coordPair.x, coordPair.z);
		} else {

			this.chunk = cps.getChunkAt(coordPair.x, coordPair.z);
		}

		this.sectionMask = environment.getRegion().getSectionMask();
		Debug.log("Section mask for the chunk: " + Integer.toBinaryString(sectionMask));
		Debug.log("Sections for chunk: " + Arrays.toString(chunk.getSections()));
		for (byte i = 0; i < 16; i++) {
			if ((sectionMask & (1 << i)) == 0)
				continue;

			sections[i] = new Section(stream, i);
		}

		int tiles = stream.readInt();
		for (int i = 0; i < tiles; i++) {
			NBTTagCompound compound = NBTHelper.loadPayload(stream, new NBTReadLimiter(2097152L));
			TileEntity tile = TileEntity.c(compound);

			Debug.log("Loading tile with NBT " + compound.toString());

			environment.getTileEntities().add(tile);
			setTileEntity(tile.getPosition(), tile);
		}
	}

	@Override
	public void serialize(DataOutputStream stream) throws IOException {
		stream.writeInt(coordPair.x);
		stream.writeInt(coordPair.z);
		Debug.log("Serializing chunk @ " + coordPair.x + " , " + coordPair.z);

		for (Section virtualChunkSection : sections) {
			if (virtualChunkSection == null)
				continue;

			virtualChunkSection.serialize(stream);
		}

		stream.writeInt(tileEntities.size());
		for (TileEntity tile : tileEntities.values()) {
			NBTTagCompound compound = new NBTTagCompound();
			tile.b(compound);

			Debug.log("Saved tile with NBT " + compound.toString());

			NBTHelper.writePayload(stream, compound);
		}
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof VirtualChunk))
			return false;
		VirtualChunk chunk = (VirtualChunk) other;
		return chunk.getCoordPair().equals(getCoordPair());
	}

	public TileEntity getTileEntity(BlockPosition pos, EnumTileEntityState entityState) {
		TileEntity tileEntity = tileEntities.get(pos);
		if (tileEntity == null) {
			if (entityState == EnumTileEntityState.IMMEDIATE) {
				Block block = this.getType(pos);

				tileEntity = block.isTileEntity() ? ((IContainer) block).a(this.world, getBlockAsLegacyData(pos))
						: null;
				environment.setTileEntity(pos, tileEntity);
			} else if (entityState == EnumTileEntityState.QUEUED)
				throw new NotImplementedException();
		} else if (tileEntity.x()) {
			this.tileEntities.remove(pos);
			return null;
		}

		return tileEntity;
	}

	public int getBlockAsLegacyData(BlockPosition blockposition) {
		IBlockData blockData = getBlockData(blockposition);
		return blockData.getBlock().toLegacyData(blockData);
	}

	public Block getType(BlockPosition pos) {
		return getBlockData(pos).getBlock();
	}

	public IBlockData getBlockData(byte x, int y, byte z) {
		if (y >= 0 && y >> 4 < this.sections.length) {
			Section section = sections[y >> 4];
			if (section != null)
				return section.getType(x, (byte) (y & 15), z);
		}
		return Blocks.AIR.getBlockData();
	}

	public IBlockData getBlockData(BlockPosition pos) {
		byte x = (byte) (pos.getX() & 15);
		byte z = (byte) (pos.getZ() & 15);

		return getBlockData(x, pos.getY(), z);
	}

	/*
	 * Data structure:
	 * yPos (1 byte)
	 * nonEmptyBlockCount (2 bytes)
	 * 
	 * array with length 4096:
	 * - blockId (2 bytes)
	 */
	public class Section implements IDataHolder {
		private @Getter byte chunkY;
		private char nonEmptyBlockCount;
		private @Getter char[] blockIds = new char[4096];

		public Section(ChunkSection section) {
			try {
				chunkY = (byte) (section.getYPosition() >> 4);
				blockIds = section.getIdArray().clone();
				nonEmptyBlockCount = (char) nonEmptyBlockCountField.getInt(section);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		public Section(byte chunkY) {
			this.chunkY = chunkY;
		}

		public Section(DataInputStream stream, byte chunkY) throws IOException {
			this.chunkY = chunkY;
			deserialize(stream);
		}

		public VirtualChunk getVirtualChunk() {
			return VirtualChunk.this;
		}

		@Override
		public void deserialize(DataInputStream stream) throws IOException {
			this.nonEmptyBlockCount = stream.readChar();
			CuboidRegion region = getEnvironment().getRegion();

			ChunkSection real = getChunk().getSections()[chunkY];
			this.blockIds = real != null ? real.getIdArray().clone() : new char[4096];

			try {
				region.forEachInside(this, (x, y, z) -> {
					try {
						blockIds[(y & 0xF) << 8 | (z & 0xF) << 4 | (x & 0xF)] = stream.readChar();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}

		@Override
		public void serialize(DataOutputStream stream) throws IOException {
			stream.writeChar(this.nonEmptyBlockCount);

			CuboidRegion region = getEnvironment().getRegion();
			try {
				region.forEachInside(this, (x, y, z) -> {
					try {
						stream.writeChar(blockIds[(y & 0xF) << 8 | (z & 0xF) << 4 | (x & 0xF)]);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}

		public IBlockData getType(byte x, byte y, byte z) {
			IBlockData ibd = Block.d.a(this.blockIds[y << 8 | z << 4 | x]);
			return ibd == null ? Blocks.AIR.getBlockData() : ibd;
		}

		public Block getBlock(byte x, byte y, byte z) {
			return getType(x, y, z).getBlock();
		}

		public boolean isEmpty() {
			return this.nonEmptyBlockCount == 0;
		}

		public void setBlock(byte x, byte y, byte z, IBlockData blockData) {
			IBlockData curr = getType(x, y, z);
			Block currBlock = curr.getBlock();
			Block newBlock = blockData.getBlock();
			if (currBlock != Blocks.AIR)
				--this.nonEmptyBlockCount;
			if (newBlock != Blocks.AIR)
				++this.nonEmptyBlockCount;

			blockIds[y << 8 | z << 4 | x] = (char) Block.d.b(blockData);
		}
	}

	static {
		try {
			nonEmptyBlockCountField = ChunkSection.class.getDeclaredField("nonEmptyBlockCount");
			nonEmptyBlockCountField.setAccessible(true);

			xCoordField = PacketPlayOutMapChunk.class.getDeclaredField("a");
			xCoordField.setAccessible(true);
			zCoordField = PacketPlayOutMapChunk.class.getDeclaredField("b");
			zCoordField.setAccessible(true);
			chunkMapField = PacketPlayOutMapChunk.class.getDeclaredField("c");
			chunkMapField.setAccessible(true);

		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
	}
}
