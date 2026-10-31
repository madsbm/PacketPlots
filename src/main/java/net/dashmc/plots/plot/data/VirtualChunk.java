package net.dashmc.plots.plot.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;
import lombok.Getter;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockContainer;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;

/**
 * A chunk's part of an environment: the sections of the chunk the region is in,
 * which are copied from the real chunk, and their tile entities.
 */
public class VirtualChunk {
	private static final FieldAccess<Integer> nonEmptyBlockCountField = new FieldAccess<>(ChunkSection.class,
			"nonEmptyBlockCount", int.class);

	private final @Getter ChunkCoordIntPair coordPair;

	private Chunk chunk;
	private final @Getter IEnvironment environment;
	private final World world;
	private final @Getter Section[] sections = new Section[16];
	private final Map<BlockPosition, TileEntity> tileEntities = new HashMap<>();
	private @Getter char sectionMask;

	private VirtualChunk(IEnvironment environment, ChunkCoordIntPair coordPair) {
		this.environment = environment;
		this.coordPair = coordPair;
		this.world = environment.getRealWorld();
		this.chunk = ((WorldServer) world).chunkProviderServer.getChunkAt(coordPair.x, coordPair.z);
		this.sectionMask = environment.getRegion().getSectionMask();
	}

	/**
	 * Copies the chunk's part of the environment's region from the real world,
	 * leaving out its tile entities.
	 */
	public static VirtualChunk copyOf(IEnvironment environment, ChunkCoordIntPair coordPair) {
		VirtualChunk virtualChunk = new VirtualChunk(environment, coordPair);
		virtualChunk.copyRealSections();
		return virtualChunk;
	}

	/**
	 * Creates the chunk without sections, for EnvironmentStore to restore the
	 * saved ones into.
	 */
	public static VirtualChunk withoutSections(IEnvironment environment, ChunkCoordIntPair coordPair) {
		return new VirtualChunk(environment, coordPair);
	}

	private void copyRealSections() {
		ChunkSection[] real = chunk.getSections();
		for (byte i = 0; i < 16; i++) {
			if ((sectionMask & 1 << i) == 0)
				continue;

			sections[i] = real[i] != null ? new Section(real[i]) : new Section(i);
		}

		Debug debug = environment.getDebug();
		debug.log(() -> "vChunk sections: " + Arrays.toString(chunk.getSections()));
		debug.log(() -> "tiles: " + chunk.getTileEntities());

		// tile entities outside the region stay, as setBlock leaves them be
		for (BlockPosition pos : chunk.getTileEntities().keySet()) {
			setBlock(pos, Blocks.AIR.getBlockData());
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
	 * @return whether the block changed, which it doesn't outside the environment
	 */
	public boolean setBlock(BlockPosition pos, IBlockData blockData) {
		byte relX = (byte) (pos.getX() & 15);
		byte relZ = (byte) (pos.getZ() & 15);
		byte yPos = (byte) (pos.getY() >> 4);

		if (!environment.contains(pos))
			return false;

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

		// like block1.remove(this.world, pos, currentBlock), which for a container
		// also removes its tile entity
		if (currBlock != newBlock) {
			environment.getBlockHandlers().remove(environment, pos, currentBlock);

			if (currBlock instanceof IContainer)
				removeTileEntity(pos);
		}

		if (section.getBlock(relX, (byte) (pos.getY() & 15), relZ) != newBlock)
			return false;

		if (currBlock instanceof IContainer) {
			TileEntity tileEntity = getTileEntity(pos, EnumTileEntityState.CHECK);
			if (tileEntity != null)
				tileEntity.E();
		}

		if (currBlock != newBlock && newBlock instanceof BlockContainer) {
			environment.getBlockHandlers().onPlace(environment, pos, blockData);
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
		environment.getDebug().log(() -> "sending tiles in chunk to player: " + tileEntities.size());
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

	// like World#t (removeTileEntity), which BlockContainer#remove calls
	private void removeTileEntity(BlockPosition pos) {
		TileEntity tileEntity = tileEntities.remove(pos);
		if (tileEntity == null)
			return;

		tileEntity.y();
		environment.getTileEntities().remove(tileEntity);

		// where vanilla drops the contents of a removed container, they go to the owner
		if (tileEntity instanceof IInventory)
			environment.getBlockBag().addAll(((IInventory) tileEntity).getContents());
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
			IBlockData ibd = Block.d.a(this.blockIds[Utils.getSectionIndex(x, y, z)]);
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

			blockIds[Utils.getSectionIndex(x, y, z)] = (char) Block.d.b(blockData);
		}
	}
}
