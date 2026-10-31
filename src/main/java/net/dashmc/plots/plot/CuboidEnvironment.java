package net.dashmc.plots.plot;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import com.google.common.collect.Lists;

import lombok.Getter;
import lombok.Setter;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.pipeline.RenderPipelineFactory;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
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

/**
 * An environment of a cuboid region of a world. Saved and loaded by
 * EnvironmentStore.
 */
public class CuboidEnvironment implements IEnvironment {
	private final HashSet<VirtualConnection> connections = new HashSet<>();
	private final HashMap<Long, VirtualChunk> virtualChunks = new HashMap<>();
	private @Getter final List<TileEntity> tileEntities = Lists.newArrayList();
	private final net.minecraft.server.v1_8_R3.World nmsWorld;
	private @Getter final UUID ownerUuid;
	private @Getter final CuboidRegion region;
	private @Getter @Setter RenderPipeline renderPipeline = RenderPipelineFactory.createEmptyPipeline();
	private @Getter final BlockBag blockBag;
	private final EnvironmentContext context;

	private CuboidEnvironment(Player owner, World world, CuboidRegion region, EnvironmentContext context) {
		this.ownerUuid = owner.getUniqueId();
		this.context = context;
		this.blockBag = new BlockBag(((CraftPlayer) owner).getHandle().inventory, context.getEvents(),
				context.getDebug());
		this.nmsWorld = ((CraftWorld) world).getHandle();
		this.region = region;
	}

	/**
	 * Creates a new environment for the owner, copying the region from the world.
	 */
	public static CuboidEnvironment create(Player owner, World world, CuboidRegion region,
			EnvironmentContext context) {
		CuboidEnvironment environment = new CuboidEnvironment(owner, world, region, context);
		Debug debug = context.getDebug();

		debug.log(() -> Arrays.toString(region.getChunks()));

		for (ChunkCoordIntPair coord : region.getChunks()) {
			debug.log(() -> "creating chunk @ " + coord.toString());
			VirtualChunk chunk = VirtualChunk.copyOf(environment, coord);
			debug.log(() -> "created chunk " + chunk);
			environment.addChunk(chunk);
		}

		return environment;
	}

	/**
	 * Creates an environment without chunks, for EnvironmentStore to add the saved
	 * ones to.
	 */
	static CuboidEnvironment withoutChunks(Player owner, World world, CuboidRegion region,
			EnvironmentContext context) {
		return new CuboidEnvironment(owner, world, region, context);
	}

	void addChunk(VirtualChunk chunk) {
		virtualChunks.put(Utils.getChunkKey(chunk.getCoordPair().x, chunk.getCoordPair().z), chunk);
	}

	@Override
	public EventDispatcher getEvents() {
		return context.getEvents();
	}

	@Override
	public VirtualBlockRegistry getBlockHandlers() {
		return context.getBlockHandlers();
	}

	@Override
	public Debug getDebug() {
		return context.getDebug();
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
		return virtualChunks.get(Utils.getChunkKey(chunkX, chunkZ));
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

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkKey(pos));
		return chunk.getBlockData(pos);
	}

	@Override
	public TileEntity getTileEntity(BlockPosition pos) {
		if (!contains(pos))
			return null;

		TileEntity tileEntity = virtualChunks.get(Utils.getChunkKey(pos)).getTileEntity(pos,
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
	public boolean setBlock(BlockPosition pos, IBlockData blockData, int flags) {
		if (!this.contains(pos))
			return false;

		VirtualChunk chunk = virtualChunks.get(Utils.getChunkKey(pos));
		boolean succeeded = chunk.setBlock(pos, blockData);
		if (!succeeded)
			return false;

		if ((flags & NOTIFY_NEIGHBOURS) != 0) {
			// any blocks removed due to physics will become property of environment owner
			notifyNeighbours(pos, blockBag);
		}

		if ((flags & SEND_TO_VIEWERS) != 0)
			broadcast(new VirtualBlockChangePacket(this, pos).getPacket());

		return true;
	}

	private void notifyNeighbours(BlockPosition pos, BlockBag bag) {
		notifyBlock(pos.west(), bag);
		notifyBlock(pos.east(), bag);
		notifyBlock(pos.down(), bag);
		notifyBlock(pos.up(), bag);
		notifyBlock(pos.north(), bag);
		notifyBlock(pos.south(), bag);
	}

	private void notifyBlock(BlockPosition pos, BlockBag bag) {
		IBlockData ibd = getType(pos);
		getBlockHandlers().onRelatedUpdated(ibd.getBlock(), this, pos, bag, ibd);
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
	public void setTileEntity(BlockPosition blockPosition, TileEntity tileEntity) {
		if (tileEntity != null && !tileEntity.x()) {
			tileEntities.add(tileEntity);
			virtualChunks.get(Utils.getChunkKey(blockPosition)).setTileEntity(blockPosition, tileEntity);
		}
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
