package net.dashmc.plots.plot;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;
import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.Setter;
import net.dashmc.plots.data.IDataHolder;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.pipeline.RenderPipelineFactory;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;

/*
 * Data structure:
 * player uuid (16 bytes - 2x long)
 * world uid (16 bytes - 2x long)
 *
 * region pos1 (x,y,z 12 bytes - 3x int)
 * region pos2 (x,y,z 12 bytes - 3x int)
 * combined blockdata in sequence z => y => x (so to unpack, loop x => y => z)
 */
public class CuboidEnvironment implements IEnvironment, IDataHolder {
	private final HashSet<VirtualConnection> connections = new HashSet<>();
	private HashMap<Integer, VirtualChunk> virtualChunks = new HashMap<>();
	private @Getter final List<TileEntity> tileEntities = Lists.newArrayList();
	private World world;
	private net.minecraft.server.v1_8_R3.World nmsWorld;
	private @Getter UUID ownerUuid;
	private @Getter CuboidRegion region;
	private @Getter @Setter RenderPipeline renderPipeline = RenderPipelineFactory.createEmptyPipeline();

	/**
	 * Creates a new environment for the owner, copying the region from the world.
	 */
	public CuboidEnvironment(UUID ownerUuid, World world, CuboidRegion region) {
		this.ownerUuid = ownerUuid;
		this.world = world;
		this.nmsWorld = ((CraftWorld) world).getHandle();
		this.region = region;

		Debug.log(Arrays.toString(region.getChunks()));

		for (ChunkCoordIntPair coord : region.getChunks()) {
			Debug.log("creating chunk @ " + coord.toString());
			VirtualChunk chunk = new VirtualChunk(this, coord);
			Debug.log("created chunk " + chunk);
			virtualChunks.put(coord.hashCode(), chunk);
		}

		// HashSet<ChunkConfig> chunks = PacketPlots.getPlotConfig().getVirtualChunks();
		// for (ChunkConfig chunk : chunks) {
		// virtualChunks.put(chunk.coords.hashCode(),
		// new VirtualChunk(this, chunk.coords, nmsWorld, chunk.getSectionsAsMask()));
		// }
	}

	/**
	 * Loads an environment from data written by {@link #serialize}.
	 */
	public CuboidEnvironment(DataInputStream stream) throws IOException {
		deserialize(stream);
	}

	@Override
	public void broadcast(Packet<?> packet) {
		if (packet == null)
			return;

		for (VirtualConnection conn : connections) {
			conn.getPlayer().playerConnection.sendPacket(packet);
		}
	}

	@Override
	public void addViewer(VirtualConnection connection) {
		connections.add(connection);
	}

	@Override
	public void removeViewer(VirtualConnection connection) {
		connections.remove(connection);
	}

	@Override
	public void close() {
		for (VirtualConnection conn : connections) {
			if (conn.getOriginal() == this)
				continue;

			// we visit locally here to prevent concurrent modification.
			// it doesnt matter anyway, as the connections are cleared after anyway!
			IEnvironment newEnv = conn.visitLocally(conn.getOriginal());
			newEnv.addViewer(conn);
		}

		connections.clear();
		virtualChunks.clear();
	}

	@Override
	public VirtualChunk getChunk(int chunkX, int chunkZ) {
		return virtualChunks.get(Utils.getChunkCoordHash(chunkX, chunkZ));
	}

	@Override
	public Collection<VirtualChunk> getChunks() {
		return Collections.unmodifiableCollection(virtualChunks.values());
	}

	@Override
	public net.minecraft.server.v1_8_R3.World getRealWorld() {
		return nmsWorld;
	}

	@Override
	public IBlockData getType(BlockPosition pos) {
		if (!contains(pos))
			return Blocks.AIR.getBlockData();

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkCoordHash(pos));
		return chunk.getBlockData(pos);
	}

	@Override
	public TileEntity getTileEntity(BlockPosition pos) {
		if (!contains(pos))
			return null;

		TileEntity tileEntity = virtualChunks.get(Utils.getChunkCoordHash(pos)).getTileEntity(pos,
				EnumTileEntityState.IMMEDIATE);
		if (tileEntity == null) {
			for (int i = 0; i < tileEntities.size(); i++) {
				TileEntity tile = tileEntities.get(i);
				if (!tile.x() && tile.getPosition().equals(pos)) {
					tileEntity = tile;
					break;
				}
			}
		}

		return tileEntity;
	}

	@Override
	public boolean setBlock(BlockPosition pos, IBlockData blockData, int i) {
		if (!this.contains(pos))
			return false;

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkCoordHash(pos));
		boolean succeeded = chunk.setBlock(pos, blockData);
		if (succeeded) {
			// any blocks removed due to physics will become property of environment owner
			notify(pos, BlockBag.getBlockBag(getOwner()));
		}

		return succeeded;
	}

	private void notify(BlockPosition pos, BlockBag bag) {
		notifyBlock(pos.west(), bag);
		notifyBlock(pos.east(), bag);
		notifyBlock(pos.down(), bag);
		notifyBlock(pos.up(), bag);
		notifyBlock(pos.north(), bag);
		notifyBlock(pos.south(), bag);

		broadcast(new VirtualBlockChangePacket(this, pos).getPacket());
	}

	private void notifyBlock(BlockPosition pos, BlockBag bag) {
		IBlockData ibd = getType(pos);
		VirtualBlock.notify(ibd.getBlock(), this, pos, bag, ibd);
	}

	@Override
	public boolean contains(BlockPosition pos) {
		return contains(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public boolean contains(int x, int y, int z) {
		if (!(x >= -30000000 && z >= -30000000 && x < 30000000
				&& z < 30000000 && y >= 0 && y < 256))
			return false;

		return region.includes(x, y, z);
	}

	@Override
	public void deserialize(DataInputStream stream) throws IOException {
		this.ownerUuid = new UUID(stream.readLong(), stream.readLong());
		this.world = Bukkit.getWorld(new UUID(stream.readLong(), stream.readLong()));
		this.nmsWorld = ((CraftWorld) world).getHandle();

		BlockPosition pos1 = new BlockPosition(stream.readInt(), stream.readInt(), stream.readInt());
		BlockPosition pos2 = new BlockPosition(stream.readInt(), stream.readInt(), stream.readInt());
		this.region = new CuboidRegion(pos1, pos2);

		int chunks = stream.readInt();
		for (int i = 0; i < chunks; i++) {
			VirtualChunk chunk = new VirtualChunk(this, stream);
			virtualChunks.put(chunk.getCoordPair().hashCode(), chunk);
		}

		Debug.log("Loaded chunks from save file: " + virtualChunks.size());
		// TODO: if the loaded region != the saved one, delete blocks and add to block
		// bag.

		getBlockBag().deserialize(stream);
	}

	@Override
	public void setTileEntity(BlockPosition blockPosition, TileEntity tileEntity) {
		if (tileEntity != null && !tileEntity.x()) {
			tileEntities.add(tileEntity);
			virtualChunks.get(Utils.getChunkCoordHash(blockPosition)).setTileEntity(blockPosition, tileEntity);
		}
	}

	@Override
	public void serialize(DataOutputStream stream) throws IOException {
		stream.writeLong(ownerUuid.getMostSignificantBits());
		stream.writeLong(ownerUuid.getLeastSignificantBits());

		stream.writeLong(world.getUID().getMostSignificantBits());
		stream.writeLong(world.getUID().getLeastSignificantBits());

		BlockPosition pos1 = region.getPos1();
		stream.writeInt(pos1.getX());
		stream.writeInt(pos1.getY());
		stream.writeInt(pos1.getZ());

		BlockPosition pos2 = region.getPos2();
		stream.writeInt(pos2.getX());
		stream.writeInt(pos2.getY());
		stream.writeInt(pos2.getZ());

		stream.writeInt(virtualChunks.size());

		for (VirtualChunk virtualChunk : virtualChunks.values()) {
			virtualChunk.serialize(stream);
		}

		getBlockBag().serialize(stream);
	}

	@Override
	public BlockBag getBlockBag() {
		return BlockBag.getBlockBag(getOwner());
	}

	private Player getOwner() {
		return Bukkit.getPlayer(ownerUuid);
	}

	// same as World#getBlockPower and World#isEmpty
	@Override
	public int getBlockPower(BlockPosition pos, EnumDirection dir) {
		IBlockData blockData = getType(pos);
		return blockData.getBlock().b(this, pos, blockData, dir);
	}

	@Override
	public boolean isEmpty(BlockPosition pos) {
		return getType(pos).getBlock().getMaterial() == Material.AIR;
	}

}
