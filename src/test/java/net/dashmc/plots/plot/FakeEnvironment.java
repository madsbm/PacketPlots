package net.dashmc.plots.plot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.Event;

import lombok.Getter;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.pipeline.RenderPipeline;
import net.dashmc.plots.pipeline.RenderPipelineFactory;
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;

/**
 * An environment kept in memory, for tests. It records the events it calls
 * and the packets it broadcasts. There are no chunks and no real world, and
 * the owner's inventory has no player behind it. setBlock ignores its flags.
 */
public class FakeEnvironment implements IEnvironment {
	private final @Getter UUID ownerUuid = UUID.randomUUID();
	private final @Getter CuboidRegion region;
	private final Map<BlockPosition, IBlockData> blocks = new HashMap<>();
	private final Map<BlockPosition, TileEntity> tiles = new HashMap<>();
	private final @Getter List<TileEntity> tileEntities = new ArrayList<>();
	private final Set<VirtualConnection> viewers = new HashSet<>();

	private final @Getter List<Event> calledEvents = new ArrayList<>();
	private final @Getter List<Packet<?>> broadcastPackets = new ArrayList<>();
	private final @Getter EventDispatcher events = calledEvents::add;
	private final @Getter VirtualBlockRegistry blockHandlers;
	private final @Getter Debug debug = Debug.OFF;

	private final @Getter PlayerInventory ownerInventory = new PlayerInventory(null);
	private final @Getter BlockBag blockBag = new BlockBag(ownerInventory, events, debug);

	/**
	 * An environment of the chunk at 0, 0, whose blocks all do what vanilla does.
	 */
	public FakeEnvironment() {
		this(new VirtualBlockRegistry());
	}

	/**
	 * An environment of the chunk at 0, 0.
	 */
	public FakeEnvironment(VirtualBlockRegistry blockHandlers) {
		this(new CuboidRegion(new BlockPosition(0, 0, 0), new BlockPosition(15, 255, 15)), blockHandlers);
	}

	public FakeEnvironment(CuboidRegion region, VirtualBlockRegistry blockHandlers) {
		this.region = region;
		this.blockHandlers = blockHandlers;
	}

	@Override
	public IBlockData getType(BlockPosition pos) {
		IBlockData data = blocks.get(pos);
		return data != null ? data : Blocks.AIR.getBlockData();
	}

	@Override
	public TileEntity getTileEntity(BlockPosition pos) {
		return tiles.get(pos);
	}

	@Override
	public boolean isEmpty(BlockPosition pos) {
		return getType(pos).getBlock().getMaterial() == Material.AIR;
	}

	@Override
	public int getBlockPower(BlockPosition pos, EnumDirection dir) {
		return 0;
	}

	@Override
	public boolean setBlock(BlockPosition pos, IBlockData data, int flags) {
		if (!contains(pos))
			return false;

		blocks.put(pos, data);
		return true;
	}

	@Override
	public boolean contains(BlockPosition pos) {
		return region.includes(pos);
	}

	@Override
	public boolean contains(int x, int y, int z) {
		return region.includes(x, y, z);
	}

	@Override
	public World getRealWorld() {
		return null;
	}

	@Override
	public void broadcast(Packet<?> packet) {
		broadcastPackets.add(packet);
	}

	@Override
	public VirtualChunk getChunk(int chunkX, int chunkZ) {
		return null;
	}

	@Override
	public Collection<VirtualChunk> getChunks() {
		return Collections.emptyList();
	}

	@Override
	public RenderPipeline getRenderPipeline() {
		return RenderPipelineFactory.createEmptyPipeline();
	}

	@Override
	public void addViewer(VirtualConnection connection) {
		viewers.add(connection);
	}

	@Override
	public void removeViewer(VirtualConnection connection) {
		viewers.remove(connection);
	}

	@Override
	public void setTileEntity(BlockPosition pos, TileEntity tile) {
		tiles.put(pos, tile);
		tileEntities.add(tile);
	}

	@Override
	public void close() {
		viewers.clear();
	}
}
