package net.dashmc.plots.plot.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import lombok.Getter;
import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;

/**
 * A chunk's part of an environment: the sections of the chunk the region is in,
 * which are copied from the real chunk, and their tile entities, which only
 * the chunk keeps. What else happens when a block changes is up to the
 * environment.
 */
public class VirtualChunk {
	private static final FieldAccess<Integer> nonEmptyBlockCountField = new FieldAccess<>(ChunkSection.class,
			"nonEmptyBlockCount", int.class);

	private final @Getter ChunkCoordIntPair coordPair;

	private Chunk chunk;
	private final World world;
	private final @Getter Section[] sections = new Section[16];
	private final Map<BlockPosition, TileEntity> tileEntities = new HashMap<>();
	private @Getter char sectionMask;

	private VirtualChunk(World world, CuboidRegion region, ChunkCoordIntPair coordPair) {
		this.coordPair = coordPair;
		this.world = world;
		this.chunk = ((WorldServer) world).chunkProviderServer.getChunkAt(coordPair.x, coordPair.z);
		this.sectionMask = region.getSectionMask();
	}

	/**
	 * Copies the chunk's part of the region from the real world, leaving out its
	 * tile entities.
	 */
	public static VirtualChunk copyOf(World world, CuboidRegion region, ChunkCoordIntPair coordPair) {
		VirtualChunk virtualChunk = new VirtualChunk(world, region, coordPair);
		virtualChunk.copyRealSections(region);
		return virtualChunk;
	}

	/**
	 * Creates the chunk without sections, for EnvironmentStore to restore the
	 * saved ones into.
	 */
	public static VirtualChunk withoutSections(World world, CuboidRegion region, ChunkCoordIntPair coordPair) {
		return new VirtualChunk(world, region, coordPair);
	}

	private void copyRealSections(CuboidRegion region) {
		ChunkSection[] real = chunk.getSections();
		for (byte i = 0; i < 16; i++) {
			if ((sectionMask & 1 << i) == 0)
				continue;

			sections[i] = real[i] != null ? new Section(real[i]) : new Section(i);
		}

		// tile entities outside the region stay
		for (BlockPosition pos : chunk.getTileEntities().keySet()) {
			if (region.includes(pos))
				setBlockData(pos, Blocks.AIR.getBlockData());
		}
	}

	/**
	 * Puts a section of the save file in the chunk.
	 */
	public void restoreSection(byte chunkY, char nonEmptyBlockCount, char[] blockIds) {
		sections[chunkY] = new Section(chunkY, nonEmptyBlockCount, blockIds);
		sectionMask |= 1 << chunkY;
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

	/**
	 * Only stores the block. The rest of what happens when a block changes, such as
	 * its tile entity, is up to CuboidEnvironment#setBlock.
	 *
	 * @param pos a position in the chunk
	 * @return false if the block is air in a section the chunk doesn't have, so
	 *         nothing was stored
	 */
	public boolean setBlockData(BlockPosition pos, IBlockData blockData) {
		byte yPos = (byte) (pos.getY() >> 4);

		Section section = sections[yPos];
		if (section == null) {
			if (blockData.getBlock() == Blocks.AIR)
				return false;

			ChunkSection real = getChunk().getSections()[yPos];
			section = real != null ? new Section(real) : new Section(yPos);
			sections[yPos] = section;
			sectionMask |= 1 << yPos;
		}

		section.setBlock((byte) (pos.getX() & 15), (byte) (pos.getY() & 15), (byte) (pos.getZ() & 15), blockData);
		return true;
	}

	public void sendTiles(EntityPlayer to) {
		Iterator<TileEntity> tiles = tileEntities.values().iterator();
		while (tiles.hasNext()) {
			Packet<?> updatePacket = tiles.next().getUpdatePacket();
			if (updatePacket == null)
				continue;

			to.playerConnection.sendPacket(updatePacket);
		}
	}

	/**
	 * Keeps the tile entity, if the block at the position has one, in place of
	 * the one it had.
	 */
	public void setTileEntity(BlockPosition pos, TileEntity entity) {
		if (entity == null || NmsTileEntities.isInvalid(entity))
			return;

		NmsTileEntities.setPosition(entity, pos);
		if (getBlockData(pos).getBlock() instanceof IContainer) {
			if (tileEntities.containsKey(pos)) {
				NmsTileEntities.invalidate(tileEntities.get(pos));
			}

			NmsTileEntities.validate(entity);
			this.tileEntities.put(pos, entity);
		}
	}

	/**
	 * Like World#t (removeTileEntity), which BlockContainer#remove calls.
	 *
	 * @return the removed tile entity, or null if there was none
	 */
	public TileEntity removeTileEntity(BlockPosition pos) {
		TileEntity tileEntity = tileEntities.remove(pos);
		if (tileEntity != null)
			NmsTileEntities.invalidate(tileEntity);

		return tileEntity;
	}

	public Collection<TileEntity> getTileEntities() {
		return Collections.unmodifiableCollection(tileEntities.values());
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

				tileEntity = block.isTileEntity() ? NmsBlocks.createTileEntity(this.world, getBlockData(pos)) : null;
				setTileEntity(pos, tileEntity);
			} else if (entityState == EnumTileEntityState.QUEUED)
				throw new NotImplementedException();
		} else if (NmsTileEntities.isInvalid(tileEntity)) {
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

	public class Section {
		private @Getter byte chunkY;
		private @Getter char nonEmptyBlockCount;
		private @Getter char[] blockIds = new char[4096];

		public Section(ChunkSection section) {
			chunkY = (byte) (section.getYPosition() >> 4);
			blockIds = section.getIdArray().clone();
			nonEmptyBlockCount = (char) nonEmptyBlockCountField.get(section).intValue();
		}

		public Section(byte chunkY) {
			this.chunkY = chunkY;
		}

		private Section(byte chunkY, char nonEmptyBlockCount, char[] blockIds) {
			this.chunkY = chunkY;
			this.nonEmptyBlockCount = nonEmptyBlockCount;
			this.blockIds = blockIds;
		}

		public IBlockData getType(byte x, byte y, byte z) {
			IBlockData ibd = NmsBlocks.getStateById(this.blockIds[Utils.getSectionIndex(x, y, z)]);
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

			blockIds[Utils.getSectionIndex(x, y, z)] = (char) NmsBlocks.getStateId(blockData);
		}
	}
}
