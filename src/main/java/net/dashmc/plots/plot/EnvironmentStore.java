package net.dashmc.plots.plot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.World;
import org.bukkit.entity.Player;

import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.plot.data.VirtualChunk.Section;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.helpers.NBTHelper;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.TileEntity;

/**
 * Saves environments in their owners' save files, and loads them from them.
 *
 * Save file structure:
 * version length (4 bytes - int)
 * version (2 bytes per char)
 * owner uuid (16 bytes - 2x long)
 * world uid (16 bytes - 2x long)
 * region pos1 (x,y,z 12 bytes - 3x int)
 * region pos2 (x,y,z 12 bytes - 3x int)
 * chunk count (4 bytes - int)
 * per chunk:
 * - chunk x and z (8 bytes - 2x int)
 * - per section of the region, from the bottom:
 * -- nonEmptyBlockCount (2 bytes - char)
 * -- block id of each position in both the region and the section, in the
 * order of CuboidRegion#forEachInSection (2 bytes each - char)
 * - tile entity count (4 bytes - int)
 * - the tile entities (NBT each)
 * block bag item count (4 bytes - int)
 * the block bag's items (NBT each)
 */
public class EnvironmentStore {
	private final File dataDirectory;
	private final String pluginVersion;
	private final Function<UUID, World> worlds;
	private final EnvironmentContext context;
	private final Logger logger;

	/**
	 * @param worlds  the loaded worlds, by uid
	 * @param context what the loaded environments are given
	 */
	public EnvironmentStore(File dataDirectory, String pluginVersion, Function<UUID, World> worlds,
			EnvironmentContext context, Logger logger) {
		this.dataDirectory = dataDirectory;
		this.pluginVersion = pluginVersion;
		this.worlds = worlds;
		this.context = context;
		this.logger = logger;

		dataDirectory.mkdirs();
	}

	/**
	 * @return the owner's saved environment, or null if they have none
	 */
	public CuboidEnvironment load(Player owner) throws IOException {
		File dataFile = getDataFile(owner.getUniqueId());
		if (!dataFile.exists())
			return null;

		try (DataInputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)))) {
			readVersion(stream);
			return readEnvironment(owner, stream);
		}
	}

	public void save(CuboidEnvironment environment) {
		UUID ownerUuid = environment.getOwnerUuid();
		File dataFile = getDataFile(ownerUuid);
		File tempFile = new File(dataDirectory, ownerUuid + ".dat.tmp");

		// write to a temporary file first, so a failed save never replaces the previous
		// one
		try (DataOutputStream stream = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(tempFile)))) {
			writeVersion(stream);
			writeEnvironment(environment, stream);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to save the PacketPlot of " + ownerUuid, e);
			tempFile.delete();
			return;
		}

		try {
			Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to replace the save file of " + ownerUuid, e);
		}
	}

	private File getDataFile(UUID ownerUuid) {
		return new File(dataDirectory, ownerUuid + ".dat");
	}

	private void writeVersion(DataOutputStream stream) throws IOException {
		stream.writeInt(pluginVersion.length());
		for (char versionCodeChar : pluginVersion.toCharArray())
			stream.writeChar(versionCodeChar);
	}

	private String readVersion(DataInputStream stream) throws IOException {
		StringBuilder versionCode = new StringBuilder();
		int versionLength = stream.readInt();
		for (int i = 0; i < versionLength; i++)
			versionCode.append(stream.readChar());

		String generatedWith = versionCode.toString();
		if (!generatedWith.equalsIgnoreCase(pluginVersion)) {
			// TODO: apply some sort of migration or different loading style

			logger.warning("Save file differs in versions! Trying to load anyway... (" + generatedWith + " -> "
					+ pluginVersion + ")");
		}

		return generatedWith;
	}

	// environment

	private void writeEnvironment(CuboidEnvironment environment, DataOutputStream stream) throws IOException {
		writeUuid(environment.getOwnerUuid(), stream);
		writeUuid(environment.getRealWorld().getWorld().getUID(), stream);

		CuboidRegion region = environment.getRegion();
		writePosition(region.getPos1(), stream);
		writePosition(region.getPos2(), stream);

		Collection<VirtualChunk> chunks = environment.getChunks();
		stream.writeInt(chunks.size());
		for (VirtualChunk chunk : chunks) {
			writeChunk(chunk, region, stream);
		}

		writeBlockBag(environment.getBlockBag(), stream);
	}

	private CuboidEnvironment readEnvironment(Player owner, DataInputStream stream) throws IOException {
		UUID ownerUuid = readUuid(stream);
		if (!ownerUuid.equals(owner.getUniqueId()))
			throw new IOException("Mismatched UUIDs: (" + owner.getUniqueId() + " => " + ownerUuid
					+ "). File might be corrupt");

		UUID worldUid = readUuid(stream);
		World world = worlds.apply(worldUid);
		if (world == null)
			throw new IOException("The world " + worldUid + " of the save file isn't loaded");

		BlockPosition pos1 = readPosition(stream);
		BlockPosition pos2 = readPosition(stream);
		CuboidRegion region = new CuboidRegion(pos1, pos2);
		CuboidEnvironment environment = CuboidEnvironment.withoutChunks(owner, world, region, context);

		int chunks = stream.readInt();
		for (int i = 0; i < chunks; i++) {
			environment.addChunk(readChunk(environment.getRealWorld(), region, stream));
		}

		context.getDebug().log(() -> "Loaded chunks from save file: " + environment.getChunks().size());
		// TODO: if the loaded region != the saved one, delete blocks and add to block
		// bag.

		readBlockBag(stream, environment.getBlockBag());
		return environment;
	}

	private static void writeUuid(UUID uuid, DataOutputStream stream) throws IOException {
		stream.writeLong(uuid.getMostSignificantBits());
		stream.writeLong(uuid.getLeastSignificantBits());
	}

	private static UUID readUuid(DataInputStream stream) throws IOException {
		return new UUID(stream.readLong(), stream.readLong());
	}

	private static void writePosition(BlockPosition pos, DataOutputStream stream) throws IOException {
		stream.writeInt(pos.getX());
		stream.writeInt(pos.getY());
		stream.writeInt(pos.getZ());
	}

	private static BlockPosition readPosition(DataInputStream stream) throws IOException {
		return new BlockPosition(stream.readInt(), stream.readInt(), stream.readInt());
	}

	// chunks

	private void writeChunk(VirtualChunk chunk, CuboidRegion region, DataOutputStream stream) throws IOException {
		ChunkCoordIntPair coordPair = chunk.getCoordPair();
		Debug debug = context.getDebug();

		stream.writeInt(coordPair.x);
		stream.writeInt(coordPair.z);
		debug.log(() -> "Serializing chunk @ " + coordPair.x + " , " + coordPair.z);

		for (Section section : chunk.getSections()) {
			if (section == null)
				continue;

			writeSection(chunk, region, section, stream);
		}

		Collection<TileEntity> tiles = chunk.getTileEntities();
		stream.writeInt(tiles.size());
		for (TileEntity tile : tiles) {
			NBTTagCompound compound = new NBTTagCompound();
			tile.b(compound);

			debug.log(() -> "Saved tile with NBT " + compound.toString());

			NBTHelper.writePayload(stream, compound);
		}
	}

	private VirtualChunk readChunk(net.minecraft.server.v1_8_R3.World world, CuboidRegion region,
			DataInputStream stream) throws IOException {
		ChunkCoordIntPair coordPair = new ChunkCoordIntPair(stream.readInt(), stream.readInt());
		Debug debug = context.getDebug();

		debug.log(() -> "Deserializing chunk @ " + coordPair.x + " , " + coordPair.z);

		VirtualChunk chunk = VirtualChunk.withoutSections(world, region, coordPair);
		char sectionMask = region.getSectionMask();
		debug.log(() -> "Section mask for the chunk: " + Integer.toBinaryString(sectionMask));
		debug.log(() -> "Sections for chunk: " + Arrays.toString(chunk.getChunk().getSections()));
		for (byte i = 0; i < 16; i++) {
			if ((sectionMask & (1 << i)) == 0)
				continue;

			readSection(chunk, region, i, stream);
		}

		int tiles = stream.readInt();
		for (int i = 0; i < tiles; i++) {
			NBTTagCompound compound = NBTHelper.loadPayload(stream);
			TileEntity tile = TileEntity.c(compound);

			debug.log(() -> "Loading tile with NBT " + compound.toString());

			chunk.setTileEntity(tile.getPosition(), tile);
		}

		return chunk;
	}

	private static void writeSection(VirtualChunk chunk, CuboidRegion region, Section section,
			DataOutputStream stream) throws IOException {
		stream.writeChar(section.getNonEmptyBlockCount());

		char[] blockIds = section.getBlockIds();
		forEachInSection(chunk, region, section.getChunkY(),
				(x, y, z) -> stream.writeChar(blockIds[Utils.getSectionIndex(x, y, z)]));
	}

	/**
	 * Reads a section into the chunk. The positions outside the region, which
	 * aren't saved, are copied from the real chunk.
	 */
	private static void readSection(VirtualChunk chunk, CuboidRegion region, byte chunkY, DataInputStream stream)
			throws IOException {
		char nonEmptyBlockCount = stream.readChar();

		ChunkSection real = chunk.getChunk().getSections()[chunkY];
		char[] blockIds = real != null ? real.getIdArray().clone() : new char[4096];
		forEachInSection(chunk, region, chunkY, (x, y, z) -> blockIds[Utils.getSectionIndex(x, y, z)] = stream.readChar());

		chunk.restoreSection(chunkY, nonEmptyBlockCount, blockIds);
	}

	/**
	 * Calls the consumer for each position in both the region and the section of
	 * the chunk.
	 */
	private static void forEachInSection(VirtualChunk chunk, CuboidRegion region, int chunkY,
			CoordinateIO consumer) throws IOException {
		ChunkCoordIntPair coordPair = chunk.getCoordPair();

		try {
			region.forEachInSection(coordPair.x, chunkY, coordPair.z, (x, y, z) -> {
				try {
					consumer.accept(x, y, z);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Reads or writes the save file at a position.
	 */
	@FunctionalInterface
	private interface CoordinateIO {
		void accept(int x, int y, int z) throws IOException;
	}

	// block bag

	static void writeBlockBag(BlockBag bag, DataOutputStream stream) throws IOException {
		stream.writeInt(bag.size());
		for (ItemStack item : bag.getBag()) {
			NBTTagCompound compound = new NBTTagCompound();
			item.save(compound);
			NBTHelper.writePayload(stream, compound);
		}
	}

	static void readBlockBag(DataInputStream stream, BlockBag bag) throws IOException {
		int items = stream.readInt();
		for (int i = 0; i < items; i++) {
			NBTTagCompound compound = NBTHelper.loadPayload(stream);
			bag.restore(ItemStack.createStack(compound));
		}
	}
}
