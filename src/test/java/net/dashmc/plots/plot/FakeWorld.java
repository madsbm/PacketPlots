package net.dashmc.plots.plot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.World;

/**
 * The blocks of an environment kept in memory, for tests of block handlers. It
 * records the packets it broadcasts. There's no real world, and setBlock
 * ignores its flags.
 */
public class FakeWorld implements IVirtualWorld {
	private final CuboidRegion region;
	private final Map<BlockPosition, IBlockData> blocks = new HashMap<>();
	private final Map<BlockPosition, TileEntity> tiles = new HashMap<>();
	private final @Getter List<Packet<?>> broadcastPackets = new ArrayList<>();

	/**
	 * The blocks of the chunk at 0, 0.
	 */
	public FakeWorld() {
		this(new CuboidRegion(new BlockPosition(0, 0, 0), new BlockPosition(15, 255, 15)));
	}

	public FakeWorld(CuboidRegion region) {
		this.region = region;
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

	public void setTileEntity(BlockPosition pos, TileEntity tile) {
		tiles.put(pos, tile);
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
}
