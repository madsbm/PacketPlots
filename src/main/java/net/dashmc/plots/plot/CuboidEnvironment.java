package net.dashmc.plots.plot;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

import lombok.Getter;
import lombok.Setter;
import net.dashmc.plots.nms.NmsBlocks;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.Chunk.EnumTileEntityState;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.IInventory;
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
	private final net.minecraft.server.v1_8_R3.World nmsWorld;
	private @Getter final UUID ownerUuid;
	private @Getter final CuboidRegion region;
	// read by outgoing interceptors on the Netty thread, set by other plugins
	private volatile @Getter @Setter RenderPipeline renderPipeline = new RenderPipeline();
	private @Getter final BlockBag blockBag;
	private final EnvironmentContext context;

	private CuboidEnvironment(Player owner, World world, CuboidRegion region, EnvironmentContext context) {
		this.ownerUuid = owner.getUniqueId();
		this.context = context;
		this.blockBag = new BlockBag(NmsPlayers.getHandle(owner).inventory, context.getEvents(), context.getDebug());
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
			VirtualChunk chunk = VirtualChunk.copyOf(environment.nmsWorld, region, coord);
			debug.log(() -> "vChunk sections: " + Arrays.toString(chunk.getChunk().getSections()));
			debug.log(() -> "tiles: " + chunk.getChunk().getTileEntities());
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
	public void broadcast(Packet<?> packet) {
		if (packet == null)
			return;

		for (VirtualConnection conn : connections) {
			conn.getPlayer().playerConnection.sendPacket(packet);
		}
	}

	@Override
	public Collection<VirtualConnection> getViewers() {
		return Collections.unmodifiableCollection(connections);
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

		return virtualChunks.get(Utils.getChunkKey(pos)).getTileEntity(pos, EnumTileEntityState.IMMEDIATE);
	}

	@Override
	public boolean setBlock(BlockPosition pos, IBlockData blockData, int flags) {
		if (!this.contains(pos))
			return false;

		if (!changeBlock(virtualChunks.get(Utils.getChunkKey(pos)), pos, blockData))
			return false;

		if ((flags & NOTIFY_NEIGHBOURS) != 0) {
			// any blocks removed due to physics will become property of environment owner
			notifyNeighbours(pos, blockBag);
		}

		if ((flags & SEND_TO_VIEWERS) != 0)
			broadcast(new VirtualBlockChangePacket(this, pos).getPacket());

		return true;
	}

	/**
	 * Changes the block in its chunk like Chunk#a: the old block is removed, which
	 * for a container also removes its tile entity, and then the new one is
	 * placed.
	 *
	 * @return whether the block changed
	 */
	private boolean changeBlock(VirtualChunk chunk, BlockPosition pos, IBlockData blockData) {
		IBlockData currentBlock = chunk.getBlockData(pos);
		if (currentBlock == blockData || !chunk.setBlockData(pos, blockData))
			return false;

		Block currBlock = currentBlock.getBlock();
		Block newBlock = blockData.getBlock();
		VirtualBlockRegistry blockHandlers = context.getBlockHandlers();

		if (currBlock != newBlock) {
			blockHandlers.handlerOf(currBlock).remove(currBlock, this, pos, currentBlock);

			if (currBlock instanceof IContainer)
				removeTileEntity(chunk, pos);
		}

		if (chunk.getType(pos) != newBlock)
			return false;

		if (currBlock instanceof IContainer) {
			TileEntity tileEntity = chunk.getTileEntity(pos, EnumTileEntityState.CHECK);
			if (tileEntity != null)
				NmsTileEntities.resetBlockCache(tileEntity);
		}

		if (currBlock != newBlock) {
			blockHandlers.handlerOf(newBlock).onPlace(newBlock, this, pos, blockData);
		}

		if (newBlock instanceof IContainer) {
			TileEntity tileEntity = chunk.getTileEntity(pos, EnumTileEntityState.CHECK);
			if (tileEntity == null) {
				tileEntity = NmsBlocks.createTileEntity(nmsWorld, blockData);
				chunk.setTileEntity(pos, tileEntity);
			}

			if (tileEntity != null)
				NmsTileEntities.resetBlockCache(tileEntity);
		}

		return true;
	}

	private void removeTileEntity(VirtualChunk chunk, BlockPosition pos) {
		TileEntity tileEntity = chunk.removeTileEntity(pos);

		// where vanilla drops the contents of a removed container, they go to the owner
		if (tileEntity instanceof IInventory)
			blockBag.addAll(((IInventory) tileEntity).getContents());
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
		Block block = ibd.getBlock();
		context.getBlockHandlers().handlerOf(block).onRelatedUpdated(block, this, pos, bag, ibd);
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
